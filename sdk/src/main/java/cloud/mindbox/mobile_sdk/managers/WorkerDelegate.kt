package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.work.ListenableWorker
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.services.WorkerType
import io.paperdb.Paper
import java.util.concurrent.CountDownLatch

internal fun sendEventsWithResult(
    context: Context,
    parent: Any,
    workerType: WorkerType
): ListenableWorker.Result {
    Logger.d(parent, "Start working...")

    try {
        Paper.init(context.applicationContext)

        val eventKeys = DbManager.getFilteredEventsKeys()
        if (eventKeys.isNullOrEmpty()) {
            Logger.d(parent, "Events list is empty")
            ListenableWorker.Result.success()
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
        Logger.e(parent, "Failed events work", e)
        ListenableWorker.Result.failure()
    }
    return ListenableWorker.Result.success()
}

private fun sendEvents(context: Context, eventKeys: List<String>, parent: Any) {
    eventKeys.forEach { eventKey ->
        val countDownLatch = CountDownLatch(1)

        val event = DbManager.getEvent(eventKey) ?: return@forEach

        GatewayManager.sendEvent(context, event) { isSended ->
            if (isSended) {
                DbManager.removeEventFromQueue(event.transactionId)
            }
            countDownLatch.countDown()
        }

        try {
            countDownLatch.await()
        } catch (e: InterruptedException) {
            Logger.e(parent, "doWork -> sending was interrupted", e)
        }
    }
}

internal fun logEndWork(parent: Any) {
    Logger.d(parent, "onStopped work")
}