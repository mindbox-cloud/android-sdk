package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import android.view.View
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.Closeable

internal class EmbeddedBlockContentController(
    private val placeSystemName: String? = null,
    configTimeout: Milliseconds = Constants.Embedded.defaultConfigTimeout,
    private val readyTimeout: Milliseconds = Constants.WebView.readyTimeout,
    private val providerFactory: (InAppType.Embedded, Timestamp) -> EmbeddedContentProvider?,
    private val blocksRegistry: () -> EmbeddedBlocksRegistry? = {
        if (MindboxDI.isInitialized()) MindboxDI.appModule.embeddedBlocksRegistry else null
    },
    private val now: () -> Timestamp = { Timestamp(System.currentTimeMillis()) },
) : EmbeddedBlockHandle {

    var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    val contentView: View?
        get() = provider?.contentView

    override val isActive: Boolean
        get() = isStarted && !isReleased

    private var provider: EmbeddedContentProvider? = null
    private var isStarted = false
    private var isReleased = false
    private var lastReportedState: EmbeddedBlockState? = null

    private var registration: Closeable? = null
    private var configJob: Job? = null

    private var pendingContent: InAppType.Embedded? = null
    private var hasPendingContent = false

    /** What the shown page was built from — the "same content" dedup key. */
    private var appliedDescriptor: PageDescriptor? = null

    /**
     * The applied content, split the way the dedup needs it: [isSamePage] is the page's identity —
     * the winner and the address the page was built from — while [params] are data a live page can
     * take over the bridge. A changed address is a different page even under the same winner.
     */
    private data class PageDescriptor(
        val inAppId: String,
        val baseUrl: String?,
        val contentUrl: String?,
        val params: Map<String, String>,
    ) {
        fun isSamePage(other: PageDescriptor): Boolean =
            inAppId == other.inAppId && baseUrl == other.baseUrl && contentUrl == other.contentUrl
    }

    private fun descriptorOf(inAppId: String, layer: Layer.WebViewLayer): PageDescriptor =
        PageDescriptor(
            inAppId = inAppId,
            baseUrl = layer.baseUrl,
            contentUrl = layer.contentUrl,
            params = layer.params,
        )

    private var updateEpoch = 0

    /** When the user started waiting for the current attempt — the base of `timeToDisplay`. */
    private var attemptStartedAt: Timestamp? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val configWaitDuration: Milliseconds = sanitizedConfigTimeout(configTimeout, placeSystemName)

    // The budgets count the user's waiting time: paused with the block, the remainder preserved,
    // the full budget restored only by a new attempt.
    private val configBudget = EmbeddedBlockWaitBudget(configWaitDuration, mainHandler) { onConfigTimeout() }
    private val readyBudget = EmbeddedBlockWaitBudget(readyTimeout, mainHandler) { onReadyTimeout() }

    fun start() {
        if (isReleased) return
        isStarted = true
        val place = placeSystemName ?: run {
            report(EmbeddedBlockState.Empty)
            return
        }

        if (lastReportedState == EmbeddedBlockState.Failed) {
            mindboxLogI("[EmbeddedBlock] Returning to a failed block, dropping its content")
            dropProvider()
            appliedDescriptor = null
        }

        if (hasPendingContent) {
            val deferred = pendingContent
            hasPendingContent = false
            pendingContent = null
            applyResolved(deferred)
        }

        provider?.let { current ->
            current.onStateChange = { state -> report(state) }
            readyBudget.armIfNeeded()
            runCatching { current.start() }.onFailure { error ->
                mindboxLogE("[EmbeddedBlock] Starting content for '$placeSystemName' crashed, reporting failure", error)
                dropProvider()
                report(EmbeddedBlockState.Failed)
            }
        }

        if (!ensureRegistered()) {
            beginWaitingForContent()
            return
        }
        if (provider == null && !hasPendingContent) {
            beginWaitingForContent()
        }
        blocksRegistry()?.onBlockAppeared(place)
    }

    fun pause() {
        isStarted = false
        // A pause, not a reset: leaving the screen does not cancel an attempt already started,
        // and the clocks stop with the user's waiting.
        readyBudget.pause()
        configBudget.pause()
        provider?.pause()
    }

    fun release() {
        isStarted = false
        isReleased = true
        readyBudget.reset()
        configBudget.reset()
        stopWaitingForDi()
        registration?.let { loggingRunCatching { it.close() } }
        registration = null
        dropProvider()
    }

    override fun onContentResolved(content: InAppType.Embedded?) {
        if (isReleased) return
        configBudget.reset()
        if (!isActive) {
            mindboxLogI("[EmbeddedBlock] Content for '$placeSystemName' arrived while paused, deferring it")
            pendingContent = content
            hasPendingContent = true
            return
        }
        applyResolved(content)
    }

    private fun applyResolved(content: InAppType.Embedded?) {
        if (content == null) {
            mindboxLogI("[EmbeddedBlock] Nothing to show for place '$placeSystemName'")
            dropProvider()
            appliedDescriptor = null
            report(EmbeddedBlockState.Empty)
            return
        }
        val layer = content.layers.filterIsInstance<Layer.WebViewLayer>().firstOrNull() ?: run {
            mindboxLogE("[EmbeddedBlock] Winner ${content.inAppId} has no webview layer, reporting failure")
            report(EmbeddedBlockState.Failed)
            return
        }
        val descriptor = descriptorOf(content.inAppId, layer)
        val current = provider

        if (current != null && descriptor == appliedDescriptor &&
            lastReportedState != EmbeddedBlockState.Failed
        ) {
            mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} for '$placeSystemName', keeping the content")
            return
        }
        if (lastReportedState == EmbeddedBlockState.Failed) {
            mindboxLogI("[EmbeddedBlock] Delivery for a failed block '$placeSystemName', rebuilding the content")
            recreateProvider(content)
            return
        }
        if (current is EmbeddedUpdatableContentProvider &&
            appliedDescriptor?.isSamePage(descriptor) == true &&
            lastReportedState == EmbeddedBlockState.Ready
        ) {
            mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} with new params, updating the content in place")
            val epoch = ++updateEpoch
            runCatching {
                current.updateParams(layer.params) { isUpdated ->
                    mainHandler.post {
                        if (isReleased || provider !== current || epoch != updateEpoch) return@post
                        if (isUpdated) {
                            appliedDescriptor = descriptor
                        } else {
                            mindboxLogW("[EmbeddedBlock] In-place update over the bridge failed, recreating the content")
                            recreateProvider(content)
                        }
                    }
                }
            }.onFailure { error ->
                mindboxLogW("[EmbeddedBlock] In-place update crashed ($error), recreating the content")
                recreateProvider(content)
            }
            return
        }
        recreateProvider(content)
    }

    private fun recreateProvider(content: InAppType.Embedded) {
        dropProvider()
        // A revival or a replacement is a new attempt; the first content of an attempt keeps the
        // clock that started with the resolve, so the wait for the answer stays in the measure.
        if (lastReportedState != EmbeddedBlockState.Loading) {
            attemptStartedAt = now()
        }
        val startedAt = attemptStartedAt ?: now().also { freshStart -> attemptStartedAt = freshStart }
        val created = loggingRunCatching(defaultValue = null) { providerFactory(content, startedAt) } ?: run {
            mindboxLogE("[EmbeddedBlock] Could not build content for ${content.inAppId}, reporting failure")
            report(EmbeddedBlockState.Failed)
            return
        }
        provider = created
        appliedDescriptor =
            descriptorOf(content.inAppId, content.layers.filterIsInstance<Layer.WebViewLayer>().first())
        created.onStateChange = { state -> report(state) }
        if (isStarted) {
            // The answer arrived and a page is being built: the wait changes its nature, so the
            // budget starts over with the page's own — shorter — patience.
            readyBudget.reset()
            readyBudget.armIfNeeded()
            runCatching { created.start() }.onFailure { error ->
                mindboxLogE("[EmbeddedBlock] Starting content for '$placeSystemName' crashed, reporting failure", error)
                dropProvider()
                report(EmbeddedBlockState.Failed)
            }
        }
    }

    private fun report(state: EmbeddedBlockState) {
        if (state !is EmbeddedBlockState.Loading) {
            // The attempt is over, with an outcome: the page budget and the attempt clock restart
            // with the next one.
            readyBudget.reset()
            attemptStartedAt = null
        }
        if (state == lastReportedState) return
        lastReportedState = state
        onStateChange?.let { listener -> loggingRunCatching { listener(state) } }
    }

    private fun ensureRegistered(): Boolean {
        if (registration != null) return true
        val place = placeSystemName ?: return false
        val registry = blocksRegistry() ?: run {
            waitForDi()
            return false
        }
        stopWaitingForDi()
        registration = registry.register(place, this)
        return true
    }

    private fun waitForDi() {
        if (configJob != null) return
        report(EmbeddedBlockState.Loading)
        configJob = loggingRunCatching(defaultValue = null) {
            MindboxPreferences.inAppConfigFlow
                .onEach {
                    mainHandler.post {
                        if (isReleased || registration != null) return@post
                        if (ensureRegistered() && isStarted) {
                            placeSystemName?.let { place -> blocksRegistry()?.onBlockAppeared(place) }
                        }
                    }
                }
                .launchIn(Mindbox.mindboxScope)
        }
    }

    private fun stopWaitingForDi() {
        val job = configJob ?: return
        configJob = null
        loggingRunCatching { job.cancel() }
    }

    /** The wait for the first answer begins: the shimmer, the config budget and the attempt clock. */
    private fun beginWaitingForContent() {
        report(EmbeddedBlockState.Loading)
        attemptStartedAt = attemptStartedAt ?: now()
        if (!hasEverResolved()) {
            configBudget.armIfNeeded()
        }
    }

    private fun onConfigTimeout() {
        if (isReleased || hasEverResolved()) return
        mindboxLogW(
            "[EmbeddedBlock] No config within ${configWaitDuration.interval}ms of waiting for " +
                "'$placeSystemName', collapsing; a late config still expands the block"
        )
        report(EmbeddedBlockState.Empty)
    }

    private fun hasEverResolved(): Boolean = provider != null || hasPendingContent || appliedDescriptor != null

    private fun onReadyTimeout() {
        if (!isStarted || provider == null) return
        mindboxLogW(
            "[EmbeddedBlock] Page for '$placeSystemName' stayed silent for " +
                "${readyTimeout.interval}ms of waiting, reporting failure",
        )
        loggingRunCatching { provider?.pause() }
        report(EmbeddedBlockState.Failed)
    }

    private fun dropProvider() {
        readyBudget.reset()
        provider?.let { current -> loggingRunCatching { current.release() } }
        provider = null
    }

    private companion object {
        /** A non-positive timeout would collapse every block before the config had a chance. */
        fun sanitizedConfigTimeout(requested: Milliseconds, place: String?): Milliseconds {
            if (requested.interval > 0) return requested
            mindboxLogE(
                "[EmbeddedBlock] Block for place '$place' was given configTimeout " +
                    "${requested.interval}ms: it must be positive, using the default " +
                    "${Constants.Embedded.defaultConfigTimeout.interval}ms"
            )
            return Constants.Embedded.defaultConfigTimeout
        }
    }
}
