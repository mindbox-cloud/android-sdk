package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendFailureWithContext
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.Closeable

internal class EmbeddedBlockContentController(
    placeSystemName: String? = null,
    configTimeout: Milliseconds = Constants.Embedded.defaultConfigTimeout,
    private val readyTimeout: Milliseconds = Constants.WebView.readyTimeout,
    private val providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider?,
    private val blocksRegistry: () -> EmbeddedBlocksRegistry? = {
        loggingRunCatching(defaultValue = null) {
            if (MindboxDI.isInitialized()) MindboxDI.appModule.embeddedBlocksRegistry else null
        }
    },
    private val monotonicNow: () -> Milliseconds = { Milliseconds(SystemClock.elapsedRealtime()) },
    private val failureTracker: () -> InAppFailureTracker? = {
        if (MindboxDI.isInitialized()) MindboxDI.appModule.inAppFailureTracker else null
    },
    private val isTagsFeatureEnabled: () -> Boolean = {
        MindboxDI.isInitialized() && MindboxDI.appModule.featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE)
    },
    private val hasConfig: () -> Boolean = {
        loggingRunCatching(defaultValue = false) {
            MindboxDI.isInitialized() && MindboxDI.appModule.mobileConfigRepositoryIfCreated?.hasConfig() == true
        }
    },
) : EmbeddedBlockHandle {

    private val placeSystemName: String? = placeSystemName?.trim()?.takeIf { it.isNotEmpty() }

    var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    val contentView: View?
        get() = provider?.contentView

    override val isActive: Boolean
        get() = isStarted && !isReleased && !hasGivenUp

    private var provider: EmbeddedContentProvider? = null
    private var isStarted = false
    private var isReleased = false
    private var hasGivenUp = false
    private var lastReportedState: EmbeddedBlockState? = null

    private var registration: Closeable? = null
    private var configJob: Job? = null

    private var pendingContent: InAppType.Embedded? = null
    private var hasPendingContent = false

    /** What the shown page was built from — the "same content" dedup key. */
    private var appliedDescriptor: PageDescriptor? = null

    /** The snapshot of the applied content — the failure events' id and tags come from it. */
    private var appliedContent: InAppType.Embedded? = null

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

    private var attemptStartTick: Milliseconds? = null

    private var pendingSinceTick: Milliseconds? = null

    private var hasPendingDelivery = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val configWaitDuration: Milliseconds = sanitizedConfigTimeout(configTimeout, placeSystemName)

    private val configBudget = EmbeddedBlockWaitBudget(configWaitDuration, mainHandler) { onConfigTimeout() }
    private val readyBudget = EmbeddedBlockWaitBudget(readyTimeout, mainHandler) { onReadyTimeout() }

    fun start() {
        if (isReleased) return
        isStarted = true
        hasGivenUp = false
        val place = placeSystemName ?: run {
            report(EmbeddedBlockState.Empty)
            return
        }

        if (lastReportedState?.nothingToShow == true) {
            mindboxLogI("[EmbeddedBlock] Returning to a block with nothing on it, asking for its content again")
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
        notifyContentDropped()
    }

    override fun onContentResolved(content: InAppType.Embedded?) {
        if (isReleased) return
        if (hasGivenUp) {
            mindboxLogI(
                "[EmbeddedBlock] Content for '$placeSystemName' arrived after the block gave up " +
                    "waiting, dropping it; the next appearance on screen asks afresh"
            )
            notifyContentDropped()
            return
        }
        configBudget.reset()
        // The pending window closes at the delivery, not at the application: a delivery deferred
        // while the block is off screen keeps the off-screen span inside the measure — only the
        // campaign's delay leaves it.
        settlePendingWindow()
        if (!isActive) {
            mindboxLogI("[EmbeddedBlock] Content for '$placeSystemName' arrived while paused, deferring it")
            pendingContent = content
            hasPendingContent = true
            return
        }
        applyResolved(content)
    }

    override fun onContentPending() {
        if (isReleased || hasGivenUp) return
        configBudget.reset()
        hasPendingDelivery = true
        pendingSinceTick = pendingSinceTick ?: monotonicNow()
    }

    private fun settlePendingWindow() {
        hasPendingDelivery = false
        pendingSinceTick?.let { pendingSince ->
            attemptStartTick = attemptStartTick?.let { started ->
                Milliseconds(started.interval + (monotonicNow().interval - pendingSince.interval))
            }
        }
        pendingSinceTick = null
    }

    private fun applyResolved(content: InAppType.Embedded?) {
        if (content == null) {
            mindboxLogI("[EmbeddedBlock] Nothing to show for place '$placeSystemName'")
            dropProvider()
            appliedDescriptor = null
            appliedContent = null
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

        if (current != null && descriptor == appliedDescriptor && lastReportedState?.nothingToShow != true) {
            mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} for '$placeSystemName', keeping the content")
            refreshMetricsSnapshot(current, content)
            return
        }
        if (lastReportedState?.nothingToShow == true) {
            mindboxLogI("[EmbeddedBlock] Delivery for a collapsed block '$placeSystemName', rebuilding the content")
            recreateProvider(content)
            return
        }
        if (current is EmbeddedUpdatableContentProvider &&
            appliedDescriptor?.isSamePage(descriptor) == true &&
            lastReportedState == EmbeddedBlockState.Ready
        ) {
            mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} with new params, updating the content in place")
            refreshMetricsSnapshot(current, content)
            val epoch = ++updateEpoch
            runCatching {
                current.updateParams(layer.params) { isUpdated ->
                    mainHandler.post {
                        if (isReleased || provider !== current || epoch != updateEpoch) return@post
                        if (isUpdated) {
                            appliedDescriptor = descriptor
                            appliedContent = content
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

    private fun refreshMetricsSnapshot(current: EmbeddedContentProvider, content: InAppType.Embedded) {
        if (current !is EmbeddedUpdatableContentProvider) return
        val applied = appliedContent
        if (applied != null && applied.frequency == content.frequency && applied.tags == content.tags) return
        mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId}: the config changed its frequency/tags, refreshing the snapshot")
        loggingRunCatching { current.refreshMetricsSnapshot(content.frequency, content.tags) }
        appliedContent = content
    }

    private fun recreateProvider(content: InAppType.Embedded) {
        dropProvider()
        if (lastReportedState != EmbeddedBlockState.Loading) {
            attemptStartTick = monotonicNow()
        }
        val startTick = attemptStartTick ?: monotonicNow().also { freshStart -> attemptStartTick = freshStart }
        val created = loggingRunCatching(defaultValue = null) { providerFactory(content, startTick) } ?: run {
            mindboxLogE("[EmbeddedBlock] Could not build content for ${content.inAppId}, reporting failure")
            report(EmbeddedBlockState.Failed)
            return
        }
        provider = created
        appliedDescriptor =
            descriptorOf(content.inAppId, content.layers.filterIsInstance<Layer.WebViewLayer>().first())
        appliedContent = content
        created.onStateChange = { state -> report(state) }
        if (isStarted) {
            readyBudget.reset()
            readyBudget.armIfNeeded()
            runCatching { created.start() }.onFailure { error ->
                mindboxLogE("[EmbeddedBlock] Starting content for '$placeSystemName' crashed, reporting failure", error)
                dropProvider()
                report(EmbeddedBlockState.Failed)
            }
        }
    }

    override val isHoldingContent: Boolean
        get() = lastReportedState is EmbeddedBlockState.Loading || lastReportedState == EmbeddedBlockState.Ready

    private fun report(state: EmbeddedBlockState) {
        if (state !is EmbeddedBlockState.Loading) {
            readyBudget.reset()
            attemptStartTick = null
        }
        if (state == lastReportedState) return
        lastReportedState = state
        onStateChange?.let { listener -> loggingRunCatching { listener(state) } }
        if (state == EmbeddedBlockState.Failed || state == EmbeddedBlockState.Empty) notifyContentDropped()
    }

    private fun notifyContentDropped() {
        val place = placeSystemName ?: return
        loggingRunCatching { blocksRegistry()?.onBlockContentDropped(place) }
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

    private fun beginWaitingForContent() {
        report(EmbeddedBlockState.Loading)
        attemptStartTick = attemptStartTick ?: monotonicNow()
        if (!hasEverResolved()) {
            configBudget.armIfNeeded()
        }
    }

    private fun onConfigTimeout() {
        if (isReleased || hasEverResolved()) return
        mindboxLogW(
            "[EmbeddedBlock] No answer within ${configWaitDuration.interval}ms of waiting for " +
                "'$placeSystemName', collapsing; a later answer is dropped, the next appearance " +
                "on screen asks afresh"
        )
        hasGivenUp = true
        configBudget.reset()
        placeSystemName?.let { place ->
            failureTracker()?.let { tracker ->
                val phase = if (hasConfig()) WaitBudgetPhase.RESOLVE_PENDING else WaitBudgetPhase.CONFIG_MISSING
                loggingRunCatching { tracker.sendWaitBudgetExceeded(place, configWaitDuration, phase) }
            }
        }
        report(EmbeddedBlockState.Empty)
    }

    private fun hasEverResolved(): Boolean =
        provider != null || hasPendingContent || appliedDescriptor != null || hasPendingDelivery

    private fun onReadyTimeout() {
        if (!isStarted || provider == null) return
        mindboxLogW(
            "[EmbeddedBlock] Page for '$placeSystemName' stayed silent for " +
                "${readyTimeout.interval}ms of waiting, reporting failure",
        )
        appliedContent?.let { content ->
            failureTracker()?.let { tracker ->
                loggingRunCatching {
                    tracker.sendFailureWithContext(
                        inAppId = content.inAppId,
                        failureReason = FailureReason.PRESENTATION_FAILED,
                        errorDescription = "The embedded block page stayed silent for " +
                            "${readyTimeout.interval}ms after the content was handed to it",
                        tags = content.tags.gatedTags(isTagsFeatureEnabled()),
                    )
                }
            }
        }
        hasGivenUp = true
        loggingRunCatching { provider?.pause() }
        report(EmbeddedBlockState.Failed)
    }

    private fun dropProvider() {
        readyBudget.reset()
        provider?.let { current -> loggingRunCatching { current.release() } }
        provider = null
    }

    private companion object {
        fun sanitizedConfigTimeout(requested: Milliseconds, place: String?): Milliseconds {
            if (requested.interval > 0) return requested
            mindboxLogE(
                "[EmbeddedBlock] Block for place '$place' was given timeout " +
                    "${requested.interval}ms: it must be positive, using the default " +
                    "${Constants.Embedded.defaultConfigTimeout.interval}ms"
            )
            return Constants.Embedded.defaultConfigTimeout
        }
    }
}
