package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxLogger
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.services.WorkerType
import java.util.concurrent.CountDownLatch

internal fun sendEventsWithResult(
    context: Context,
    parent: Any,
    workerType: WorkerType
): ListenableWorker.Result {
    MindboxLogger.d(parent, "Start working...")

    try {
        Mindbox.initComponents(context)

        val eventKeys = DbManager.getFilteredEventsKeys()
        if (eventKeys.isNullOrEmpty()) {
            MindboxLogger.d(parent, "Events list is empty")
            return ListenableWorker.Result.success()
        } else {

            sendEvents(context, eventKeys, parent)

            return when (workerType) {
                WorkerType.ONE_TIME_WORKER -> ListenableWorker.Result.success()
                WorkerType.PERIODIC_WORKER ->
                    if (!DbManager.getFilteredEventsKeys().isNullOrEmpty()) {
                        ListenableWorker.Result.retry()
                    } else {
                        ListenableWorker.Result.success()
                    }
            }
        }
    } catch (e: Exception) {
        MindboxLogger.e(parent, "Failed events work", e)
        return ListenableWorker.Result.failure()
    }
}

private fun sendEvents(context: Context, eventKeys: List<String>, parent: Any) {
    runCatching {
        eventKeys.forEach { eventKey ->
            val countDownLatch = CountDownLatch(1)

            val event = DbManager.getEvent(eventKey) ?: return@forEach

            GatewayManager.sendEvent(context, event) { isSended ->
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