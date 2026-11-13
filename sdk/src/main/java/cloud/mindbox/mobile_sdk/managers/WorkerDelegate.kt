package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class WorkerDelegate {

    private companion object {
        const val MAX_DELIVERED_EVENTS_PER_RUN = 20
    }

    private class EventsDeliveryResult(
        val deliveredCount: Int = 0,
        val hasFailedDelivery: Boolean = false,
        val hasFirstThrottling: Boolean = false,
    ) {
        val isBatchDelivered: Boolean
            get() = deliveredCount == MAX_DELIVERED_EVENTS_PER_RUN

        val shouldSlowDown: Boolean
            get() = hasFirstThrottling || (hasFailedDelivery && !isBatchDelivered)
    }

    private var isWorkerStopped = false

    fun sendEventsWithResult(
        context: Context,
        parent: Any,
    ): ListenableWorker.Result {
        mindboxLogI("Start working...")

        try {
            Mindbox.initComponents(context)

            val configuration = DbManager.getConfigurations()

            if (MindboxPreferences.isFirstInitialize || configuration == null) {
                MindboxLoggerImpl.e(parent, "Configuration was not initialized")
                return ListenableWorker.Result.failure()
            }

            val events = DbManager.getFilteredEventsForBackgroundSend()
            return if (events.isEmpty()) {
                mindboxLogI("Events list is empty")
                when {
                    isWorkerStopped -> ListenableWorker.Result.failure()
                    DbManager.getFilteredEvents().isEmpty() -> ListenableWorker.Result.success()
                    else -> {
                        mindboxLogI("Database contains events that can't be sent right now. Worker will restart")
                        BackgroundWorkManager.startNextOneTimeService(context)
                        ListenableWorker.Result.success()
                    }
                }
            } else {
                MindboxLoggerImpl.d(parent, "Will be sent ${events.size}")

                val result = sendEvents(context, events, configuration, parent)

                when {
                    isWorkerStopped -> ListenableWorker.Result.failure()
                    DbManager.getFilteredEvents().isEmpty() -> ListenableWorker.Result.success()
                    result.shouldSlowDown -> ListenableWorker.Result.retry()
                    else -> {
                        if (result.isBatchDelivered) {
                            mindboxLogI("[GD] decision: $MAX_DELIVERED_EVENTS_PER_RUN events delivered, continue in the next run")
                        }
                        BackgroundWorkManager.startNextOneTimeService(context)
                        ListenableWorker.Result.success()
                    }
                }
            }
        } catch (e: Exception) {
            MindboxLoggerImpl.e(parent, "Failed events work", e)
            return ListenableWorker.Result.failure()
        }
    }

    private fun sendEvents(
        context: Context,
        events: List<Event>,
        configuration: Configuration,
        parent: Any,
    ): EventsDeliveryResult = loggingRunCatching(defaultValue = EventsDeliveryResult(hasFailedDelivery = true)) {
        val eventsCount = events.size - 1
        val deviceUuid = MindboxPreferences.deviceUuid
        val deliveryPauseManager = MindboxDI.appModule.deliveryPauseManager
        val pausedOperations = mutableSetOf<String>()
        var deliveredCount = 0
        var hasFailedDelivery = false
        var hasFirstThrottling = false

        for ((index, event) in events.withIndex()) {
            if (isWorkerStopped || deliveredCount == MAX_DELIVERED_EVENTS_PER_RUN) break
            val eventType = event.eventType
            val remainingPause = deliveryPauseManager.remainingPause(eventType)
            if (remainingPause != null) {
                if (pausedOperations.add(eventType.deliveryKey)) {
                    mindboxLogI(
                        "[GD] decision: operation ${eventType.operation} paused, postpone, ${remainingPause.toSeconds()} s left"
                    )
                }
                continue
            }

            val wasThrottled = deliveryPauseManager.wasThrottled(eventType)
            val result = sendEvent(
                context = context,
                configuration = configuration,
                deviceUuid = deviceUuid,
                event = event,
                parent = parent,
                index = index,
                eventsCount = eventsCount,
                shouldStartWorker = false,
                shouldCountOffset = true,
            )
            when (result) {
                AsyncEventSendResult.DELIVERED -> deliveredCount++
                AsyncEventSendResult.THROTTLED -> {
                    pausedOperations.add(eventType.deliveryKey)
                    if (!wasThrottled) hasFirstThrottling = true
                }
                AsyncEventSendResult.RETRY_LATER -> hasFailedDelivery = true
                AsyncEventSendResult.DISCARDED -> Unit
            }
        }
        EventsDeliveryResult(
            deliveredCount = deliveredCount,
            hasFailedDelivery = hasFailedDelivery,
            hasFirstThrottling = hasFirstThrottling,
        )
    }

    private fun sendEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        parent: Any,
        index: Int = 0,
        eventsCount: Int = 1,
        shouldStartWorker: Boolean = false,
        shouldCountOffset: Boolean = true,
    ): AsyncEventSendResult {
        val countDownLatch = CountDownLatch(1)
        var sendResult = AsyncEventSendResult.RETRY_LATER

        MindboxDI.appModule.gatewayManager.sendAsyncEvent(
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            shouldCountOffset = shouldCountOffset
        ) { result ->
            Mindbox.mindboxScope.launch {
                if (result.isRemovedFromQueue) {
                    DbManager.removeEventFromQueue(event)
                } else if (shouldStartWorker) {
                    BackgroundWorkManager.startOneTimeService(context)
                }

                MindboxLoggerImpl.i(
                    parent,
                    "sent event index #$index id #${event.uid} from $eventsCount",
                )

                sendResult = result
                countDownLatch.countDown()
            }
        }

        try {
            countDownLatch.await()
        } catch (e: InterruptedException) {
            MindboxLoggerImpl.e(parent, "doWork -> sending was interrupted", e)
        }
        MindboxDI.appModule.deliveryPauseManager.register(event.eventType, sendResult)?.let { pause ->
            mindboxLogI(
                "[GD] decision: operation ${event.eventType.operation} throttled, skip for ${pause.toSeconds()} s"
            )
        }
        return sendResult
    }

    fun sendEventOrPostpone(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        parent: Any,
    ) {
        val remainingPause = MindboxDI.appModule.deliveryPauseManager.remainingPause(event.eventType)
        if (remainingPause != null) {
            mindboxLogI(
                "[GD] decision: operation ${event.eventType.operation} paused, postpone, ${remainingPause.toSeconds()} s left"
            )
            BackgroundWorkManager.startOneTimeService(context)
            return
        }
        sendEvent(
            context = context,
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            parent = parent,
            shouldStartWorker = true,
            shouldCountOffset = false,
        )
    }

    private fun Milliseconds.toSeconds(): Long = TimeUnit.MILLISECONDS.toSeconds(interval)

    fun onEndWork(parent: Any) {
        isWorkerStopped = true
        MindboxLoggerImpl.d(parent, "onStopped work")
    }
}
