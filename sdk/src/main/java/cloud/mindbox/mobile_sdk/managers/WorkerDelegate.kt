package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

internal class WorkerDelegate {

    private var isWorkerStopped = false

    fun sendEventsWithResult(
        context: Context,
        parent: Any,
    ): ListenableWorker.Result {
        MindboxLoggerImpl.d(parent, "Start working...")

        try {
            Mindbox.initComponents(context)

            val configuration = DbManager.getConfigurations()

            if (MindboxPreferences.isFirstInitialize || configuration == null) {
                MindboxLoggerImpl.e(parent, "Configuration was not initialized")
                return ListenableWorker.Result.failure()
            }

            val events = DbManager.getFilteredEventsForBackgroundSend()
            return if (events.isNullOrEmpty()) {
                MindboxLoggerImpl.d(parent, "Events list is empty")
                if (DbManager.getFilteredEvents().isNullOrEmpty()) {
                    ListenableWorker.Result.success()
                } else {
                    MindboxLoggerImpl.d(
                        parent,
                        "Database contains events that can't be sent right now. Worker will restart",
                    )
                    ListenableWorker.Result.retry()
                }
            } else {
                MindboxLoggerImpl.d(parent, "Will be sent ${events.size}")

                sendEvents(context, events, configuration, parent)

                when {
                    isWorkerStopped -> ListenableWorker.Result.failure()
                    DbManager.getFilteredEvents().isNullOrEmpty() ->
                        ListenableWorker.Result.success()
                    else -> ListenableWorker.Result.retry()
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
    ) = LoggingExceptionHandler.runCatching {
        val eventsCount = events.size - 1
        val deviceUuid = MindboxPreferences.deviceUuid

        events.forEachIndexed { index, event ->
            if (isWorkerStopped) return@runCatching
            sendEvent(
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
        }
    }

    fun sendEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        parent: Any,
        index: Int = 0,
        eventsCount: Int = 1,
        shouldStartWorker: Boolean = false,
        shouldCountOffset: Boolean = true,
    ) {
        val countDownLatch = CountDownLatch(1)

        MindboxDI.appModule.gatewayManager.sendAsyncEvent(
            configuration = configuration,
            deviceUuid = deviceUuid,
            event = event,
            shouldCountOffset = shouldCountOffset
        ) { isSent ->
            Mindbox.mindboxScope.launch {
                if (isSent) {
                    DbManager.removeEventFromQueue(event)
                } else if (shouldStartWorker) {
                    BackgroundWorkManager.startOneTimeService(context)
                }

                MindboxLoggerImpl.i(
                    parent,
                    "sent event index #$index id #${event.uid} from $eventsCount",
                )

                countDownLatch.countDown()
            }
        }

        try {
            countDownLatch.await()
        } catch (e: InterruptedException) {
            MindboxLoggerImpl.e(parent, "doWork -> sending was interrupted", e)
        }
    }

    fun onEndWork(parent: Any) {
        isWorkerStopped = true
        MindboxLoggerImpl.d(parent, "onStopped work")
    }
}
