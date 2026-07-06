package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppWebViewLearnedHostsStore
import cloud.mindbox.mobile_sdk.inapp.data.validators.WebViewLayerValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
 * page with the official prewarm params on its URL, so the shared HTTP cache and
 * connection pool are warm before the first show. A real show always preempts the prewarm
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
    private val inAppValidator: InAppValidator,
    private val webViewLayerValidator: WebViewLayerValidator,
    private val learnedHostsStore: InAppWebViewLearnedHostsStore
) : InAppWebViewPrewarmService {

    companion object {
        // Hard cap on how long the hidden WebView may live after the content page was
        // handed to it; normally the network-idle poll below releases it much earlier
        // (cache/sockets survive at the profile level, so keeping it alive buys nothing).
        private const val SETTLE_RELEASE_MS = 30_000L

        // Network-idle release: the page is considered settled when the Resource Timing
        // entry count is stable across consecutive polls AND the last completed resource
        // finished at least IDLE_QUIET_MS ago. Entries appear only on completion, so the
        // quiet window (not the stable count alone) is what guards against an in-flight
        // download; a transfer slower than the window can still be cut — same worst case
        // as the hard cap, the show just re-fetches that file.
        private const val IDLE_POLL_MS = 1_000L
        private const val IDLE_QUIET_MS = 2_000L
        private const val IDLE_STABLE_POLLS = 2

        // Returns "<entryCount>:<msSinceLastResponseEnd>" (or "" on any error).
        private const val IDLE_PROBE_JS =
            "(function(){try{var e=performance.getEntriesByType('resource');var l=0;" +
                "for(var i=0;i<e.length;i++){var r=e[i].responseEnd;if(r>l)l=r}" +
                "return e.length+':'+Math.round(performance.now()-l)}catch(t){return''}})()"
    }

    private val hasStartedResourcePrewarm = AtomicBoolean(false)
    private val hasAborted = AtomicBoolean(false)

    // Set when the freshest config proved there is nothing to warm: an init prewarm still
    // suspended in the content fetch must not resurrect a WebView the config just retired.
    private val latestConfigHasNoLayers = AtomicBoolean(false)
    private val settleJob = AtomicReference<Job?>(null)

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
            latestConfigHasNoLayers.set(true)
            settleJob.getAndSet(null)?.cancel()
            engine.release()
            return
        }
        latestConfigHasNoLayers.set(false)
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                startResourcePrewarm(layers)
            }
        }
    }

    override fun onRealShowWillStart() {
        hasAborted.set(true)
        settleJob.getAndSet(null)?.cancel()
        // Terminal: the engine latches synchronously, so a prewarm load already posted from
        // a background thread cannot resurrect the WebView mid-show.
        engine.abort()
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

    /**
     * Runs at most once per process — but only an attempt that actually reaches the engine
     * consumes the one-shot: a transient configuration read failure or an unplannable
     * cached config must not block a later attempt from a valid fresh config.
     *
     * By design the one-shot also means stage 2 does NOT re-warm when stage 1 already ran
     * from a cached config whose URLs have since changed — the head start beats freshness
     * for this launch, and the next launch heals with the new cached config.
     */
    private suspend fun startResourcePrewarm(layers: List<InAppWebViewPrewarmLayer>) {
        if (hasAborted.get()) return

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
        if (!hasStartedResourcePrewarm.compareAndSet(false, true)) return
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
        // Re-check both verdicts after the suspension point: a real show may have taken the
        // network over, or a fresh config may have proven there is nothing to warm — either
        // way the fetched content must not resurrect a WebView.
        if (hasAborted.get() || latestConfigHasNoLayers.get()) return
        // Official prewarm contract on the document URL: a runtime that knows it boots
        // tracker-only; an older runtime ignores it (plain page warm, no byendpoint).
        val prewarmBaseUrl = InAppWebViewPrewarmPlanner.prewarmContentBaseUrl(
            baseUrl = plan.baseUrl,
            endpointId = configuration.endpointId,
            deviceUuid = MindboxPreferences.deviceUuid
        )
        mindboxLogI("[WebView] Prewarm: content page under $prewarmBaseUrl, endpoint ${configuration.endpointId}")
        engine.loadContentPage(
            html = html,
            baseUrl = prewarmBaseUrl,
            userAgentSuffix = userAgentSuffix
        )
        scheduleSettleRelease()
    }

    private fun scheduleSettleRelease() {
        // A terminal preempt may have landed while the caller was suspended — never store a
        // poll job into the slot onRealShowWillStart() just cleared (it would probe the main
        // looper for 30s during the live show).
        if (hasAborted.get()) return
        val job = Mindbox.mindboxScope.launch {
            // Poll-count cap instead of wall clock: identical budget, but the whole loop
            // runs on virtual time in tests.
            val maxPolls = (SETTLE_RELEASE_MS / IDLE_POLL_MS).toInt()
            var polls = 0
            var lastCount = -1
            var stablePolls = 0
            var idle = false
            while (polls < maxPolls) {
                delay(IDLE_POLL_MS)
                polls++
                if (hasAborted.get()) return@launch
                val probe = probeResourceState() ?: continue
                if (probe.entryCount == lastCount) {
                    stablePolls++
                } else {
                    stablePolls = 0
                    lastCount = probe.entryCount
                }
                if (stablePolls >= IDLE_STABLE_POLLS && probe.msSinceLastResponseEnd > IDLE_QUIET_MS) {
                    idle = true
                    break
                }
            }
            mindboxLogI(
                "[WebView] Prewarm: settle release after ~${polls * IDLE_POLL_MS}ms " +
                    if (idle) "(network idle, $lastCount resources)" else "(hard cap)"
            )
            engine.release()
        }
        settleJob.getAndSet(job)?.cancel()
    }

    private data class ResourceProbe(val entryCount: Int, val msSinceLastResponseEnd: Long)

    /**
     * One Resource Timing probe on the prewarm page. Null when the probe cannot run or
     * returns garbage (WebView gone/aborted, page not ready) — callers just keep polling
     * until the hard cap. The 2s timeout guards against a callback that never fires.
     */
    private suspend fun probeResourceState(): ResourceProbe? =
        withTimeoutOrNull(IDLE_QUIET_MS) {
            suspendCancellableCoroutine { continuation ->
                engine.evaluateJavaScript(IDLE_PROBE_JS) { rawResult ->
                    // evaluateJavascript JSON-quotes string results: "\"6:3456\"".
                    val parts = rawResult?.trim('"')?.split(':')
                    val probe = if (parts?.size == 2) {
                        val count = parts[0].toIntOrNull()
                        val sinceLast = parts[1].toLongOrNull()
                        if (count != null && sinceLast != null) ResourceProbe(count, sinceLast) else null
                    } else {
                        null
                    }
                    if (continuation.isActive) continuation.resume(probe) {}
                }
            }
        }

    private suspend fun currentConfiguration(): Configuration? =
        runCatching { DbManager.listenConfigurations().first() }.getOrNull()

    /** Light parse of the cached config: only webview layer urls, no targeting checks. */
    private fun webViewLayers(configString: String): List<InAppWebViewPrewarmLayer> {
        val configBlank = mobileConfigSerializationManager.deserializeToConfigDtoBlank(configString)
            ?: return emptyList()
        return configBlank.inApps.orEmpty()
            // Same version gate as the real pipeline: in-apps for other SDK versions may
            // carry form formats this version cannot even deserialize.
            .filter { inAppBlank -> inAppValidator.validateInAppVersion(inAppBlank) }
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
