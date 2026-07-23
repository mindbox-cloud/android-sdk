package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.FEATURE_TOGGLE_DEFAULT
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppWebViewLearnedHostsStore
import cloud.mindbox.mobile_sdk.inapp.data.managers.PREWARM_INAPP_WEBVIEW_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.validators.WebViewLayerValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmEngine
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmLayer
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmPlanner
import cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.Duration.Companion.milliseconds

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
internal interface InAppWebViewPrewarmManager {

    /** Prewarm stage 1: head start from the cached config. Call once at SDK init. */
    fun prewarmOnInit()

    /** Prewarm stage 2: warm what [config]'s webview in-apps need (or release when none). */
    fun prewarmResources(config: InAppConfig)

    /**
     * A real WEBVIEW show is starting: abort the prewarm and free its WebView.
     *
     * Deliberately narrow — image/snackbar shows do not preempt: their downloads are small
     * next to the settle window (network-idle release frees the WebView within seconds),
     * while aborting here is terminal and would forfeit the whole launch's byendpoint warm
     * because an unrelated banner happened to show first.
     */
    fun onRealShowWillStart()

    /** Records the https hosts the shown page actually used (learned-hosts store). */
    @OptIn(InternalMindboxApi::class)
    fun captureObservedHosts(controller: WebViewController)

    /**
     * Terminates the prewarm subsystem for good. Call before cancelling the coroutine scope
     * this manager runs on (SDK teardown, soft reinitialization): a settle-poll job killed by
     * scope cancellation never reaches its own tail-end `engine.release()`, so without this
     * the warm WebView would leak until process death.
     */
    fun terminate()
}

@OptIn(InternalMindboxApi::class)
internal class InAppWebViewPrewarmManagerImpl(
    private val engine: InAppWebViewPrewarmEngine,
    private val mobileConfigSerializationManager: MobileConfigSerializationManager,
    private val gatewayManager: Lazy<GatewayManager>,
    private val inAppValidator: InAppValidator,
    private val webViewLayerValidator: WebViewLayerValidator,
    private val learnedHostsStore: InAppWebViewLearnedHostsStore,
    private val featureToggleManager: FeatureToggleManager,
    private val webViewCachePolicy: InAppWebViewCachePolicy
) : InAppWebViewPrewarmManager {

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
        // Cheap short-circuit before paying for the config read/parse below: a repeat
        // initialize() call must not redo work a prior attempt already finished.
        if (hasStartedResourcePrewarm.get()) return
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                // Other init-time readers wait for this; without it, a migration that fails
                // and triggers a softReset could erase the cached config out from under a
                // prewarm that already read it.
                InitializeLock.await(InitializeLock.State.MIGRATION)
                val cachedConfig = MindboxPreferences.inAppConfig
                if (cachedConfig.isBlank()) {
                    // Nothing to prewarm, but the cache toggle still needs a decision for
                    // the first real show — latch it to the default now, off the main
                    // thread, instead of parsing lazily (nothing to parse anyway) the
                    // moment isCacheEnabled is first read during that show.
                    webViewCachePolicy.prime(null)
                    return@loggingRunCatchingSuspending
                }
                val layers = webViewLayers(cachedConfig)
                if (layers.isEmpty()) return@loggingRunCatchingSuspending
                mindboxLogI("[WebView] Prewarm: head start from cached config (${layers.size} webview layer(s))")
                startResourcePrewarm(layers)
            }
        }
    }

    override fun prewarmResources(config: InAppConfig) {
        // A fresh config that turns the toggle off also kills a stage-1 instance started
        // under the previous launch's config.
        if (!featureToggleManager.isEnabled(PREWARM_INAPP_WEBVIEW_FEATURE)) {
            mindboxLogI("[WebView] Prewarm: feature toggle is off, releasing warm WebView")
            releaseWarmWebView()
            return
        }
        val layers = config.inApps
            .flatMap { inApp -> inApp.form.variants }
            .filterIsInstance<InAppType.WebView>()
            .flatMap { webView -> webView.layers }
            .filterIsInstance<Layer.WebViewLayer>()
            .map { layer -> InAppWebViewPrewarmLayer(baseUrl = layer.baseUrl, contentUrl = layer.contentUrl) }
        if (layers.isEmpty()) {
            mindboxLogI("[WebView] Prewarm: config has no webview in-apps, releasing warm WebView")
            releaseWarmWebView()
            return
        }
        latestConfigHasNoLayers.set(false)
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                startResourcePrewarm(layers)
            }
        }
    }

    private fun releaseWarmWebView() {
        latestConfigHasNoLayers.set(true)
        settleJob.getAndSet(null)?.cancel()
        engine.release()
    }

    override fun onRealShowWillStart() = abortPermanently()

    override fun terminate() = abortPermanently()

    private fun abortPermanently() {
        hasAborted.set(true)
        settleJob.getAndSet(null)?.cancel()
        // Terminal: the engine latches synchronously, so a prewarm load already posted from
        // a background thread cannot resurrect the WebView afterward.
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
        // Re-check after the configuration read suspension BEFORE the one-shot CAS: an
        // attempt that stumbles here must not consume it, or a fresh no-layers config
        // landing mid-suspension would burn the one-shot on a prewarm that never reaches
        // the engine, per this function's own doc comment.
        if (hasAborted.get() || latestConfigHasNoLayers.get()) return
        if (!hasStartedResourcePrewarm.compareAndSet(false, true)) return
        val userAgentSuffix = configuration.getShortUserAgent()

        mindboxLogI("[WebView] Prewarm: preconnect to ${plan.preconnectOrigins.joinToString(",")} under ${plan.baseUrl}")
        engine.loadPreconnectPage(plan.preconnectHtml, plan.baseUrl, userAgentSuffix)

        val html = runCatching { gatewayManager.value.fetchWebViewContent(plan.contentUrl) }
            .getOrElse { error ->
                mindboxLogW("[WebView] Prewarm: content page fetch failed: $error")
                scheduleSettleRelease()
                return
            }
        // Re-check both verdicts after the suspension point: a real show may have taken the
        // network over, or a fresh config may have proven there is nothing to warm — either
        // way the fetched content must not resurrect a WebView. The no-layers path must
        // also RELEASE: the preconnect load above may have already created the WebView, and
        // with no settle poll scheduled on this path nothing else would ever free it.
        if (hasAborted.get()) return
        if (latestConfigHasNoLayers.get()) {
            engine.release()
            return
        }
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
            // Belt-and-suspenders with the per-tick check below: an abort landing between
            // the guard above and this coroutine's first resumption must not run even one
            // poll tick.
            if (hasAborted.get()) return@launch
            // Budget in poll units instead of a wall clock read: the whole loop runs on
            // virtual time in tests. A null probe is charged the full probe timeout so the
            // cap stays a real wall-clock bound even when the evaluate callback never fires
            // (blocked main thread, renderer stall — the pathological cases the cap exists
            // for). A fast-but-garbage probe gets overcharged and releases early, which is
            // the safe direction: garbage means the page or WebView is not answering.
            val budgetPolls = (SETTLE_RELEASE_MS / IDLE_POLL_MS).toInt()
            val timeoutCharge = (IDLE_QUIET_MS / IDLE_POLL_MS).toInt()
            var polls = 0
            var lastCount = -1
            var stablePolls = 0
            var idle = false
            while (polls < budgetPolls) {
                delay(IDLE_POLL_MS.milliseconds)
                polls++
                if (hasAborted.get()) return@launch
                val probe = probeResourceState()
                if (probe == null) {
                    polls += timeoutCharge
                    continue
                }
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
                "[WebView] Prewarm: settle release after ~${polls * IDLE_POLL_MS}ms of budget " +
                    if (idle) "(network idle, $lastCount resources)" else "(hard cap)"
            )
            engine.release()
        }
        settleJob.getAndSet(job)?.cancel()
        if (hasAborted.get()) {
            settleJob.getAndSet(null)?.cancel()
        }
    }

    private data class ResourceProbe(val entryCount: Int, val msSinceLastResponseEnd: Long)

    /**
     * One Resource Timing probe on the prewarm page. Null when the probe cannot run or
     * returns garbage (WebView gone/aborted, page not ready) — callers just keep polling
     * until the hard cap. The 2s timeout guards against a callback that never fires.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun probeResourceState(): ResourceProbe? =
        withTimeoutOrNull(IDLE_QUIET_MS.milliseconds) {
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

    /**
     * Light parse of the cached config: only webview layer urls, no targeting checks.
     *
     * The toggle is read from THIS cached config, not [featureToggleManager]: stage 1 races
     * the fresh config download, so the manager may still hold last launch's (or no) state
     * when this runs.
     */
    private fun webViewLayers(configString: String): List<InAppWebViewPrewarmLayer> {
        val configBlank = mobileConfigSerializationManager.deserializeToConfigDtoBlank(configString)
            ?: return emptyList()
        // Shares this parse with the cache toggle instead of it deserializing the same
        // cached config a second time; a no-op once the toggle has already latched.
        webViewCachePolicy.prime(configBlank)
        if (!isPrewarmEnabled(configBlank)) {
            mindboxLogI("[WebView] Prewarm: feature toggle is off in the cached config, skipping head start")
            return emptyList()
        }
        return configBlank.inApps.orEmpty()
            // Same version gate as the real pipeline: in-apps for other SDK versions may
            // carry form formats this version cannot even deserialize.
            .asSequence()
            .filter { inAppBlank -> inAppValidator.validateInAppVersion(inAppBlank) }
            .flatMap { inAppBlank ->
                mobileConfigSerializationManager.deserializeToInAppFormDto(inAppBlank.form)
                    ?.variants.orEmpty()
                    .filterIsInstance<PayloadDto.ModalWindowDto>()
                    // Same gate as the real pipeline (InAppMapper): a modal only becomes a
                    // WebView in-app when webview is its FIRST layer — collecting every
                    // webview layer regardless of position would prewarm modals that will
                    // never actually show as a webview, burning the one-shot on them.
                    .mapNotNull { modal -> modal.content?.background?.layers?.firstOrNull() }
            }
            .filterIsInstance<BackgroundDto.LayerDto.WebViewLayerDto>()
            .filter { layerDto -> webViewLayerValidator.isValid(layerDto) }
            .map { layerDto -> InAppWebViewPrewarmLayer(baseUrl = layerDto.baseUrl, contentUrl = layerDto.contentUrl) }
            .toList()
    }

    private fun isPrewarmEnabled(configBlank: InAppConfigResponseBlank): Boolean =
        configBlank.settings?.featureToggles?.toggles?.get(PREWARM_INAPP_WEBVIEW_FEATURE) ?: FEATURE_TOGGLE_DEFAULT

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
