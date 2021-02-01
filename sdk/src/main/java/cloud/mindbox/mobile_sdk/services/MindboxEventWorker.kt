package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import io.paperdb.Paper
import java.lang.Exception

internal class MindboxEventWorker(private val appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams)  {

    override fun doWork(): Result {
        Logger.d(this, "Start working...")

        //todo подписаться на жизненный цикл и проверить на инициализацию
        try {
            Paper.init(applicationContext)

            val events = DbManager.getEventsQueue()
            if (events.isNullOrEmpty()) {
                Logger.d(this, "Events list is empty")
                Result.retry()
            } else {
                events.forEach { event ->
                    GatewayManager.sendEvent(appContext, event) { isSended ->
                        if (isSended) {
                            DbManager.removeEventFromQueue(event.transactionId)
                        }
                    }
                }

                return if (!DbManager.getEventsQueue().isNullOrEmpty()) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Logger.e(this, "Failed events work", e)
            Result.failure()
        }
        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        Logger.d(this, "onStopped")
    }
}