package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.MindboxLogger
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.WorkerType
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import java.util.*
import java.util.concurrent.CountDownLatch

internal fun sendEventsWithResult(
    context: Context,
    parent: Any,
    workerType: WorkerType
): ListenableWorker.Result {
    MindboxLogger.d(parent, "Start working...")

    try {
        Mindbox.initComponents(context)

        // Handle SSL error for Android less 21
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(context)
            } catch (repairableException: GooglePlayServicesRepairableException) {
                MindboxLogger.e(parent, "GooglePlayServices should be updated", repairableException)
            } catch (notAvailableException: GooglePlayServicesNotAvailableException) {
                MindboxLogger.e(parent, "GooglePlayServices aren't available", notAvailableException)
            }
        }

        val configuration = DbManager.getConfigurations()

        if (MindboxPreferences.isFirstInitialize || configuration == null) {
            MindboxLogger.e(
                parent,
                "MindboxConfiguration was not initialized",
            )
            return ListenableWorker.Result.failure()
        }

        var eventKeys = DbManager.getFilteredEventsKeys()
        if (eventKeys.isNullOrEmpty()) {
            MindboxLogger.d(parent, "Events list is empty")
            return ListenableWorker.Result.success()
        } else {

            if (workerType == WorkerType.PERIODIC_WORKER && eventKeys.size > 1000) {
                eventKeys = eventKeys.subList(0, 1000)
            }

            MindboxLogger.d(parent, "Will be sent ${eventKeys.size}")

            sendEvents(context, eventKeys, configuration, parent)

            return if (DbManager.getFilteredEventsKeys().isNullOrEmpty()) {
                ListenableWorker.Result.success()
            } else {
                ListenableWorker.Result.retry()
            }
        }
    } catch (e: Exception) {
        MindboxLogger.e(parent, "Failed events work", e)
        return ListenableWorker.Result.failure()
    }
}

private fun sendEvents(context: Context, eventKeys: List<String>, configuration: MindboxConfiguration, parent: Any) {
    runCatching {

        val eventsCount = eventKeys.size - 1

        for (i in 0..eventsCount) {
            val countDownLatch = CountDownLatch(1)

            val eventKey = eventKeys[i]
            val event = DbManager.getEvent(eventKey) ?: return

            GatewayManager.sendEvent(context, configuration, event) { isSended ->
                if (isSended) {
                    DbManager.removeEventFromQueue(eventKey)
                }

                MindboxLogger.i(parent, "sent event #${i + 1} from ${eventsCount + 1}")

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

internal fun logEndWork(parent: Any) {
    MindboxLogger.d(parent, "onStopped work")
}