package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxLogger
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.services.WorkerType
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

        var eventKeys = DbManager.getFilteredEventsKeys()
        if (eventKeys.isNullOrEmpty()) {
            MindboxLogger.d(parent, "Events list is empty")
            return ListenableWorker.Result.success()
        } else {

            if (workerType == WorkerType.PERIODIC_WORKER && eventKeys.size > 1000) {
                eventKeys = eventKeys.subList(0, 1000)
            }

            MindboxLogger.d(parent, "Will be sended ${eventKeys.size}")

            sendEvents(context, eventKeys, parent)

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

private fun sendEvents(context: Context, eventKeys: List<String>, parent: Any) {
    runCatching {
        val configuration = DbManager.getConfigurations()

        if (configuration == null) {
            MindboxLogger.e(
                parent,
                "MindboxConfiguration was not initialized",
            )
            return@runCatching
        }

        eventKeys.forEach { eventKey ->
            val countDownLatch = CountDownLatch(1)

            val event = DbManager.getEvent(eventKey) ?: return@forEach

            GatewayManager.sendEvent(context, configuration, event) { isSended ->
                if (isSended) {
                    DbManager.removeEventFromQueue(eventKey)
                }

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