package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.os.Build
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch

internal class WorkerDelegate {

    private var isWorkerStopped = false

    fun sendEventsWithResult(
        context: Context,
        parent: Any
    ): ListenableWorker.Result {
        MindboxLogger.d(parent, "Start working...")

        try {
            Mindbox.initComponents(context)

            // Handle SSL error for Android less 21
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try {
                    ProviderInstaller.installIfNeeded(context)
                } catch (repairableException: GooglePlayServicesRepairableException) {
                    MindboxLogger.e(
                        parent,
                        "GooglePlayServices should be updated",
                        repairableException
                    )
                } catch (notAvailableException: GooglePlayServicesNotAvailableException) {
                    MindboxLogger.e(
                        parent,
                        "GooglePlayServices aren't available",
                        notAvailableException
                    )
                }
            }

            val configuration = DbManager.getConfigurations()

            if (MindboxPreferences.isFirstInitialize || configuration == null) {
                MindboxLogger.e(
                    parent,
                    "Configuration was not initialized",
                )
                return ListenableWorker.Result.failure()
            }

            val events = DbManager.getFilteredEvents()
            return if (events.isNullOrEmpty()) {
                MindboxLogger.d(parent, "Events list is empty")
                ListenableWorker.Result.success()
            } else {
                MindboxLogger.d(parent, "Will be sent ${events.size}")

                sendEvents(context, events, configuration, parent)

                when {
                    isWorkerStopped -> ListenableWorker.Result.failure()
                    DbManager.getFilteredEvents().isNullOrEmpty() ->
                        ListenableWorker.Result.success()
                    else -> ListenableWorker.Result.retry()
                }
            }
        } catch (e: Exception) {
            MindboxLogger.e(parent, "Failed events work", e)
            return ListenableWorker.Result.failure()
        }
    }

    private fun sendEvents(
        context: Context,
        events: List<Event>,
        configuration: Configuration,
        parent: Any
    ) {
        runCatching {

            val eventsCount = events.size - 1
            val deviceUuid = MindboxPreferences.deviceUuid

            events.forEachIndexed { index, event ->
                val countDownLatch = CountDownLatch(1)

                if (isWorkerStopped) return

                GatewayManager.sendEvent(context, configuration, deviceUuid, event) { isSent ->
                    if (isSent) {
                        handleSendResult(event)
                    }

                    MindboxLogger.i(
                        parent,
                        "sent event index #${index + 1} id #${event.uid} from $eventsCount"
                    )

                    countDownLatch.countDown()
                }

                try {
                    countDownLatch.await()
                } catch (e: InterruptedException) {
                    MindboxLogger.e(parent, "doWork -> sending was interrupted", e)
                }
            }
        }.logOnException()
    }

    fun onEndWork(parent: Any) {
        isWorkerStopped = true
        MindboxLogger.d(parent, "onStopped work")
    }

    private fun handleSendResult(
        event: Event
    ) = runBlocking(Dispatchers.IO) { DbManager.removeEventFromQueue(event) }

}
