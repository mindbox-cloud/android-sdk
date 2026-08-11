package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import android.view.View
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class EmbeddedBlockContentController(
    private val resolveFactory: () -> EmbeddedContentResolution,
    private val placeSystemName: String? = null,
    private val readyTimeout: Milliseconds = Constants.WebView.readyTimeout,
) {

    var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    val contentView: View?
        get() = provider?.contentView

    private var provider: EmbeddedContentProvider? = null
    private var isStarted = false
    private var isReleased = false
    private var isSessionListenerRegistered = false
    private var isTimeoutScheduled = false
    private var lastReportedState: EmbeddedBlockState? = null
    private var configJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onReadyTimeout() }

    // The session storage identifies listeners by reference, so add and remove must be given the
    // very same instance.
    private val onSessionExpired: () -> Unit = {
        mainHandler.post {
            if (!isReleased) {
                mindboxLogI("[EmbeddedBlock] New session, dropping the block content")
                dropProvider()
                if (isStarted) start()
            }
        }
    }

    // Null until the DI graph is built: a block can be on screen before Mindbox.init returns.
    private val sessionStorage: SessionStorageManager?
        get() = if (MindboxDI.isInitialized()) MindboxDI.appModule.sessionStorageManager else null

    fun start() {
        if (isReleased) return
        isStarted = true
        registerSessionListener()
        if (provider == null) {
            placeSystemName?.let { MindboxEventManager.embeddedPlaceRequested(it) }
        }

        val current = provider ?: resolveProvider() ?: return
        current.onStateChange = { state -> report(state) }
        scheduleTimeout()
        current.start()
    }

    fun pause() {
        isStarted = false
        cancelTimeout()
        provider?.pause()
    }

    fun release() {
        isStarted = false
        isReleased = true
        cancelTimeout()
        unregisterSessionListener()
        stopWaitingForConfig()
        dropProvider()
    }

    private fun report(state: EmbeddedBlockState) {
        // Any answer from the page settles the budget, including one the container already knows.
        if (state !is EmbeddedBlockState.Loading) cancelTimeout()
        // A page reports its height on every relayout, and each of those arrives as another
        // Ready. Repeating a state the container is already in is pure churn.
        if (state == lastReportedState) return
        lastReportedState = state
        onStateChange?.invoke(state)
    }

    private fun scheduleTimeout() {
        if (isTimeoutScheduled) return
        isTimeoutScheduled = true
        mainHandler.postDelayed(timeoutRunnable, readyTimeout.interval)
    }

    private fun cancelTimeout() {
        if (!isTimeoutScheduled) return
        isTimeoutScheduled = false
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    private fun onReadyTimeout() {
        isTimeoutScheduled = false
        if (!isStarted || provider == null) return
        mindboxLogW(
            "[EmbeddedBlock] Page for '$placeSystemName' stayed silent for " +
                "${readyTimeout.interval}ms after load, reporting failure",
        )
        pause()
        unregisterSessionListener()
        report(EmbeddedBlockState.Failed)
    }

    private fun dropProvider() {
        cancelTimeout()
        provider?.release()
        provider = null
    }

    private fun resolveProvider(): EmbeddedContentProvider? {
        val resolution = runCatching { resolveFactory() }.getOrElse { error ->
            mindboxLogE("[EmbeddedBlock] Content resolution failed, the block reports failure", error)
            report(EmbeddedBlockState.Failed)
            return null
        }
        return when (resolution) {
            is EmbeddedContentResolution.Content -> {
                stopWaitingForConfig()
                provider = resolution.provider
                resolution.provider
            }
            is EmbeddedContentResolution.NothingToShow -> {
                stopWaitingForConfig()
                report(EmbeddedBlockState.Empty)
                null
            }
            is EmbeddedContentResolution.NotReadyYet -> {
                waitForConfig()
                null
            }
        }
    }

    private fun waitForConfig() {
        report(EmbeddedBlockState.Loading)
        if (configJob != null) return
        configJob = loggingRunCatching(defaultValue = null) {
            MindboxPreferences.inAppConfigFlow
                .onEach { mainHandler.post { onConfigArrived() } }
                .launchIn(Mindbox.mindboxScope)
        }
    }

    private fun onConfigArrived() {
        if (isReleased || provider != null) return
        mindboxLogI("[EmbeddedBlock] Config arrived, resolving '$placeSystemName' again")
        // The graph is up by now — a listener the block could not take before it can take here.
        registerSessionListener()
        val resolved = resolveProvider() ?: return
        resolved.onStateChange = { state -> report(state) }
        if (isStarted) {
            scheduleTimeout()
            resolved.start()
        } else {
            resolved.pause()
        }
    }

    private fun stopWaitingForConfig() {
        val job = configJob ?: return
        configJob = null
        loggingRunCatching { job.cancel() }
    }

    private fun registerSessionListener() {
        if (isSessionListenerRegistered) return
        isSessionListenerRegistered = loggingRunCatching(defaultValue = false) {
            val storage = sessionStorage ?: return@loggingRunCatching false
            storage.addSessionExpirationListener(onSessionExpired)
            true
        }
    }

    private fun unregisterSessionListener() {
        if (!isSessionListenerRegistered) return
        isSessionListenerRegistered = false
        loggingRunCatching { sessionStorage?.removeSessionExpirationListener(onSessionExpired) }
    }
}
