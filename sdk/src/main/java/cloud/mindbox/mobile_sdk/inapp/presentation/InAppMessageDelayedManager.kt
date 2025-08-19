package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.pollIf
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.launchWithLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

internal class InAppMessageDelayedManager(private val timeProvider: TimeProvider, dispatcher: CoroutineDispatcher) {

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 2
    }

    private val sequenceNumber = AtomicLong(0)
    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob() + Mindbox.coroutineExceptionHandler)
    private var nextProcessQueueJob: Job? = null
    private val processingMutex = Mutex()
    private val pendingInAppComparator = compareBy<PendingInApp> { it.showTimeMillis }
        .thenByDescending { it.inApp.isPriority }
        .thenBy { it.sequenceNumber }

    private val pendingInApps = PriorityBlockingQueue(
        DEFAULT_INITIAL_CAPACITY,
        pendingInAppComparator
    )

    private val _inAppToShowFlow = MutableSharedFlow<InApp>()
    val inAppToShowFlow = _inAppToShowFlow.asSharedFlow()

    private data class PendingInApp(
        val inApp: InApp,
        val showTimeMillis: Long,
        val sequenceNumber: Long
    )

    internal fun process(inApp: InApp) {
        coroutineScope.launchWithLock(processingMutex) {
            mindboxLogD("Processing In-App: ${inApp.id}, Priority: ${inApp.isPriority}, Delay: ${inApp.delayTime}")
            val delay = inApp.delayTime?.interval ?: 0L
            val showTime = timeProvider.currentTimeMillis() + delay

            pendingInApps.put(
                PendingInApp(
                    inApp = inApp,
                    showTimeMillis = showTime,
                    sequenceNumber = sequenceNumber.getAndIncrement()
                )
            )
            processQueue()
        }
    }

    internal fun onAppResumed() {
        mindboxLogI("App resumed, re-evaluating scheduled In-Apps.")
        processQueue()
    }

    private fun processQueue() {
        if (pendingInApps.isEmpty()) return
        coroutineScope.launchWithLock(processingMutex) {
            nextProcessQueueJob?.cancel()

            val now = timeProvider.currentTimeMillis()

            pendingInApps.pollIf { it.showTimeMillis <= now }?.let { showCandidate ->
                mindboxLogI("Winner found: ${showCandidate.inApp.id}. Emitting to show.")
                _inAppToShowFlow.emit(showCandidate.inApp)

                do {
                    val inApp = pendingInApps.pollIf { it.showTimeMillis <= now }.also { discarded ->
                        mindboxLogI("Discarding other ready In-App: ${discarded?.inApp?.id}")
                    }
                } while (inApp != null)
            }
            scheduleNextProcess()
        }
    }

    private fun scheduleNextProcess() {
        pendingInApps.peek()?.let { nextInApp ->
            val now = timeProvider.currentTimeMillis()
            val delay = (nextInApp.showTimeMillis - now).coerceAtLeast(0)
            mindboxLogI("Scheduling next In-App ${nextInApp.inApp.id} with delay: $delay ms.")
            nextProcessQueueJob = coroutineScope.launch {
                delay(delay)
                processQueue()
            }
        }
    }

    internal fun clearSession() {
        coroutineScope.launchWithLock(processingMutex) {
            nextProcessQueueJob?.cancel()
            pendingInApps.clear()
        }
    }
}
