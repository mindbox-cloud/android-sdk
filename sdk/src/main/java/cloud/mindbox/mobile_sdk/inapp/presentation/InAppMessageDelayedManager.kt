package cloud.mindbox.mobile_sdk.inapp.presentation

import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

internal class InAppMessageDelayedManager(private val timeProvider: TimeProvider, delayedManagerDispatcher: CoroutineDispatcher) {

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 11
    }

    private val sequenceNumber = AtomicLong(0)
    private val delayedManagerCoroutineScope = CoroutineScope(delayedManagerDispatcher + SupervisorJob() + Mindbox.coroutineExceptionHandler)
    private var nextProcessQueueJob: Job? = null
    private val delayedInAppProcessingMutex = Mutex()
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
        delayedManagerCoroutineScope.launch {
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
        if (pendingInApps.isNotEmpty()) {
            processQueue()
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun processQueue() {
        delayedManagerCoroutineScope.launch {
            delayedInAppProcessingMutex.withLock {
                nextProcessQueueJob?.cancel()

                val now = timeProvider.currentTimeMillis()
                val winnerCandidate = pendingInApps.peek()

                if (winnerCandidate != null && winnerCandidate.showTimeMillis <= now) {
                    pendingInApps.poll()?.let { winner ->
                        mindboxLogI("Winner found: ${winner.inApp.id}. Emitting to show.")
                        _inAppToShowFlow.emit(winner.inApp)
                    }

                    while (pendingInApps.peek()?.showTimeMillis?.let { it <= now } == true) {
                        pendingInApps.poll()?.let { discarded ->
                            mindboxLogI("Discarding other ready In-App: ${discarded.inApp.id}")
                        }
                    }
                }
                scheduleJob()
            }
        }
    }

    private fun scheduleJob() {
        pendingInApps.peek()?.let { nextInApp ->
            val now = timeProvider.currentTimeMillis()
            val delay = (nextInApp.showTimeMillis - now).coerceAtLeast(0)
            mindboxLogI("Scheduling next In-App ${nextInApp.inApp.id} with delay: $delay ms.")
            nextProcessQueueJob = delayedManagerCoroutineScope.launch {
                delay(delay)
                processQueue()
            }
        }
    }

    internal fun clearSession() {
        delayedManagerCoroutineScope.launch {
            delayedInAppProcessingMutex.withLock {
                nextProcessQueueJob?.cancel()
                pendingInApps.clear()
            }
        }
    }
}
