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
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.Closeable

internal class EmbeddedBlockContentController(
    private val placeSystemName: String? = null,
    private val configTimeout: Milliseconds = Constants.Embedded.defaultConfigTimeout,
    private val readyTimeout: Milliseconds = Constants.WebView.readyTimeout,
    private val providerFactory: (InAppType.Embedded) -> EmbeddedContentProvider?,
    private val blocksRegistry: () -> EmbeddedBlocksRegistry? = {
        if (MindboxDI.isInitialized()) MindboxDI.appModule.embeddedBlocksRegistry else null
    },
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

    /** Winner id + webview params of the applied content — the "same winner" dedup key. */
    private var appliedDescriptor: Pair<String, Map<String, String>>? = null

    private var updateEpoch = 0

    private var isReadyTimeoutScheduled = false
    private var isConfigTimeoutScheduled = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val readyTimeoutRunnable = Runnable { onReadyTimeout() }
    private val configTimeoutRunnable = Runnable { onConfigTimeout() }

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
            scheduleReadyTimeout()
            runCatching { current.start() }.onFailure { error ->
                mindboxLogE("[EmbeddedBlock] Starting content for '$placeSystemName' crashed, reporting failure", error)
                dropProvider()
                report(EmbeddedBlockState.Failed)
            }
        }

        if (!ensureRegistered()) {
            report(EmbeddedBlockState.Loading)
            scheduleConfigTimeout()
            return
        }
        if (provider == null && !hasPendingContent) {
            report(EmbeddedBlockState.Loading)
            scheduleConfigTimeout()
        }
        blocksRegistry()?.onBlockAppeared(place)
    }

    fun pause() {
        isStarted = false
        cancelReadyTimeout()
        provider?.pause()
    }

    fun release() {
        isStarted = false
        isReleased = true
        cancelReadyTimeout()
        cancelConfigTimeout()
        stopWaitingForDi()
        registration?.let { loggingRunCatching { it.close() } }
        registration = null
        dropProvider()
    }

    override fun onContentResolved(content: InAppType.Embedded?) {
        if (isReleased) return
        cancelConfigTimeout()
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
        val descriptor = content.inAppId to layer.params
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
            appliedDescriptor?.first == content.inAppId &&
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
        val created = loggingRunCatching(defaultValue = null) { providerFactory(content) } ?: run {
            mindboxLogE("[EmbeddedBlock] Could not build content for ${content.inAppId}, reporting failure")
            report(EmbeddedBlockState.Failed)
            return
        }
        provider = created
        appliedDescriptor = content.inAppId to
            (content.layers.filterIsInstance<Layer.WebViewLayer>().first().params)
        created.onStateChange = { state -> report(state) }
        if (isStarted) {
            scheduleReadyTimeout()
            runCatching { created.start() }.onFailure { error ->
                mindboxLogE("[EmbeddedBlock] Starting content for '$placeSystemName' crashed, reporting failure", error)
                dropProvider()
                report(EmbeddedBlockState.Failed)
            }
        }
    }

    private fun report(state: EmbeddedBlockState) {
        if (state !is EmbeddedBlockState.Loading) cancelReadyTimeout()
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

    private fun scheduleConfigTimeout() {
        if (isConfigTimeoutScheduled || hasEverResolved()) return
        isConfigTimeoutScheduled = true
        mainHandler.postDelayed(configTimeoutRunnable, configTimeout.interval)
    }

    private fun cancelConfigTimeout() {
        if (!isConfigTimeoutScheduled) return
        isConfigTimeoutScheduled = false
        mainHandler.removeCallbacks(configTimeoutRunnable)
    }

    private fun onConfigTimeout() {
        isConfigTimeoutScheduled = false
        if (isReleased || hasEverResolved()) return
        mindboxLogW(
            "[EmbeddedBlock] No config within ${configTimeout.interval}ms for '$placeSystemName', " +
                "collapsing; a late config still expands the block"
        )
        report(EmbeddedBlockState.Empty)
    }

    private fun hasEverResolved(): Boolean = provider != null || hasPendingContent || appliedDescriptor != null

    private fun scheduleReadyTimeout() {
        if (isReadyTimeoutScheduled) return
        isReadyTimeoutScheduled = true
        mainHandler.postDelayed(readyTimeoutRunnable, readyTimeout.interval)
    }

    private fun cancelReadyTimeout() {
        if (!isReadyTimeoutScheduled) return
        isReadyTimeoutScheduled = false
        mainHandler.removeCallbacks(readyTimeoutRunnable)
    }

    private fun onReadyTimeout() {
        isReadyTimeoutScheduled = false
        if (!isStarted || provider == null) return
        mindboxLogW(
            "[EmbeddedBlock] Page for '$placeSystemName' stayed silent for " +
                "${readyTimeout.interval}ms after load, reporting failure",
        )
        loggingRunCatching { provider?.pause() }
        report(EmbeddedBlockState.Failed)
    }

    private fun dropProvider() {
        cancelReadyTimeout()
        provider?.let { current -> loggingRunCatching { current.release() } }
        provider = null
    }
}
