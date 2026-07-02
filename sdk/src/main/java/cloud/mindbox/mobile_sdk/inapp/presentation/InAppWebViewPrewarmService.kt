package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppWebViewLearnedHostsStore
import cloud.mindbox.mobile_sdk.inapp.data.validators.WebViewLayerValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmEngine
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmLayer
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmPlanner
import cloud.mindbox.mobile_sdk.inapp.webview.MindboxWebViewLab
import cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production prewarm for webview in-apps. Two stages, both driven by the mobile
 * config (no hardcoded hosts or URLs):
 *
 *  1. SDK init — head start from the previous launch's cached config;
 *  2. config downloaded/parsed — [prewarmResources] (releases the warm instance
 *     when the config proves there are no webview in-apps).
 *
 * The resource prewarm loads a preconnect page (origins from the config layers +
 * API domain + hosts learned from previous shows) and then the layer's real content
 * page with a stub legacy bridge, so the shared HTTP cache and connection pool are
 * warm before the first show. A real show always preempts the prewarm
 * ([onRealShowWillStart]): the hidden WebView is destroyed and the network is
 * handed over. Unlike iOS there is no instance reuse — Android shares the renderer
 * process, so a warm instance buys nothing (measured).
 */
internal interface InAppWebViewPrewarmService {

    /** Prewarm stage 1: head start from the cached config. Call once at SDK init. */
    fun prewarmOnInit()

    /** Prewarm stage 2: warm what [config]'s webview in-apps need (or release when none). */
    fun prewarmResources(config: InAppConfig)

    /** A real webview show is starting: abort the prewarm and free its WebView. */
    fun onRealShowWillStart()

    /** Records the https hosts the shown page actually used (learned-hosts store). */
    fun captureObservedHosts(controller: WebViewController)
}

@OptIn(InternalMindboxApi::class)
internal class InAppWebViewPrewarmServiceImpl(
    private val engine: InAppWebViewPrewarmEngine,
    private val mobileConfigSerializationManager: MobileConfigSerializationManager,
    private val gatewayManager: GatewayManager,
    private val webViewLayerValidator: WebViewLayerValidator,
    private val learnedHostsStore: InAppWebViewLearnedHostsStore
) : InAppWebViewPrewarmService {

    companion object {
        // How long the hidden WebView may keep downloading after the content page was
        // handed to it; afterwards the instance is destroyed (cache/sockets survive at
        // the profile level, so keeping it alive any longer buys nothing).
        private const val SETTLE_RELEASE_MS = 30_000L
    }

    private val hasStartedResourcePrewarm = AtomicBoolean(false)
    private val hasAborted = AtomicBoolean(false)
    private var settleJob: Job? = null

    override fun prewarmOnInit() {
        if (!MindboxWebViewLab.PREWARM_ENABLED) return // MEASUREMENT (throwaway) gate
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                val cachedConfig = MindboxPreferences.inAppConfig
                if (cachedConfig.isBlank()) return@loggingRunCatchingSuspending
                val layers = webViewLayers(cachedConfig)
                if (layers.isEmpty()) return@loggingRunCatchingSuspending
                mindboxLogI("[WebView] Prewarm: head start from cached config (${layers.size} webview layer(s))")
                startResourcePrewarm(layers)
            }
        }
    }

    override fun prewarmResources(config: InAppConfig) {
        if (!MindboxWebViewLab.PREWARM_ENABLED) return // MEASUREMENT (throwaway) gate
        val layers = config.inApps
            .flatMap { inApp -> inApp.form.variants }
            .filterIsInstance<InAppType.WebView>()
            .flatMap { webView -> webView.layers }
            .filterIsInstance<Layer.WebViewLayer>()
            .map { layer -> InAppWebViewPrewarmLayer(baseUrl = layer.baseUrl, contentUrl = layer.contentUrl) }
        if (layers.isEmpty()) {
            mindboxLogI("[WebView] Prewarm: config has no webview in-apps, releasing warm WebView")
            settleJob?.cancel()
            engine.release()
            return
        }
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                startResourcePrewarm(layers)
            }
        }
    }

    override fun onRealShowWillStart() {
        hasAborted.set(true)
        settleJob?.cancel()
        engine.release()
    }

    override fun captureObservedHosts(controller: WebViewController) {
        controller.evaluateJavaScript(InAppWebViewPrewarmPlanner.observedResourceHostsScript) { result ->
            val observedHosts = parseObservedHosts(result)
            if (observedHosts.isEmpty()) return@evaluateJavaScript
            Mindbox.mindboxScope.launch {
                loggingRunCatchingSuspending {
                    val endpointId = currentConfiguration()?.endpointId ?: return@loggingRunCatchingSuspending
                    learnedHostsStore.merge(endpointId, observedHosts)
                    mindboxLogI("[WebView] Prewarm: learned hosts for $endpointId: $observedHosts")
                }
            }
        }
    }

    private suspend fun startResourcePrewarm(layers: List<InAppWebViewPrewarmLayer>) {
        if (hasAborted.get()) return
        if (!hasStartedResourcePrewarm.compareAndSet(false, true)) return

        val configuration = currentConfiguration() ?: run {
            mindboxLogW("[WebView] Prewarm: no saved configuration, skipping")
            return
        }
        val plan = InAppWebViewPrewarmPlanner.buildPlan(
            layers = layers,
            extraOrigins = listOf(configuration.domain) + learnedHostsStore.hosts(configuration.endpointId)
        ) ?: run {
            mindboxLogW("[WebView] Prewarm: no valid webview layer urls in config, skipping")
            return
        }
        val userAgentSuffix = configuration.getShortUserAgent()

        mindboxLogI("[WebView] Prewarm: preconnect to ${plan.preconnectOrigins.joinToString(",")} under ${plan.baseUrl}")
        engine.loadPreconnectPage(plan.preconnectHtml, plan.baseUrl, userAgentSuffix)

        if (MindboxWebViewLab.PREWARM_PRECONNECT_ONLY) { // MEASUREMENT (throwaway) gate
            scheduleSettleRelease()
            return
        }

        val html = runCatching { gatewayManager.fetchWebViewContent(plan.contentUrl) }
            .getOrElse { error ->
                mindboxLogW("[WebView] Prewarm: content page fetch failed: $error")
                scheduleSettleRelease()
                return
            }
        if (hasAborted.get()) return
        mindboxLogI("[WebView] Prewarm: content page under ${plan.baseUrl}, endpoint ${configuration.endpointId}")
        engine.loadContentPage(
            html = html,
            baseUrl = plan.baseUrl,
            endpointId = configuration.endpointId,
            deviceUuid = MindboxPreferences.deviceUuid,
            userAgentSuffix = userAgentSuffix
        )
        scheduleSettleRelease()
    }

    private fun scheduleSettleRelease() {
        settleJob?.cancel()
        settleJob = Mindbox.mindboxScope.launch {
            delay(SETTLE_RELEASE_MS)
            engine.release()
        }
    }

    private suspend fun currentConfiguration(): Configuration? =
        runCatching { DbManager.listenConfigurations().first() }.getOrNull()

    /** Light parse of the cached config: only webview layer urls, no targeting/validation. */
    private fun webViewLayers(configString: String): List<InAppWebViewPrewarmLayer> {
        val configBlank = mobileConfigSerializationManager.deserializeToConfigDtoBlank(configString)
            ?: return emptyList()
        return configBlank.inApps.orEmpty()
            .flatMap { inAppBlank ->
                mobileConfigSerializationManager.deserializeToInAppFormDto(inAppBlank.form)
                    ?.variants.orEmpty()
                    .filterIsInstance<PayloadDto.ModalWindowDto>()
                    .flatMap { modal -> modal.content?.background?.layers.orEmpty() }
            }
            .filterIsInstance<BackgroundDto.LayerDto.WebViewLayerDto>()
            .filter { layerDto -> webViewLayerValidator.isValid(layerDto) }
            .map { layerDto -> InAppWebViewPrewarmLayer(baseUrl = layerDto.baseUrl, contentUrl = layerDto.contentUrl) }
    }

    /**
     * `evaluateJavascript` returns the JS value JSON-encoded; the probe returns a string
     * containing a JSON array, so unwrap the outer string and then parse the array.
     */
    private fun parseObservedHosts(result: String?): List<String> = loggingRunCatching(defaultValue = emptyList()) {
        if (result.isNullOrBlank() || result == "null") return@loggingRunCatching emptyList()
        val unwrapped = JSONTokener(result).nextValue() as? String ?: return@loggingRunCatching emptyList()
        val array = JSONArray(unwrapped)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { host -> host.isNotBlank() }
        }
    }
}
