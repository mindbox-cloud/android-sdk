package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import java.lang.Exception

internal class MindboxEventWorker(private val appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams)  {

    override fun doWork(): Result {
        Logger.d(this, "Start working...")

        //todo проверить на инициализацию
        try {
            val events = DbManager.getEventsStack()
            if (events.isNullOrEmpty()) {
                Logger.d(this, "Events list is empty")
                Result.failure()
            } else {
                events.forEach { event ->
                    GatewayManager.sendEvent(appContext, event) { isSended ->
                        if (isSended) {
                            DbManager.removeEventFromStack(event.transactionId)
                        }
                    }
                }

                return if (!DbManager.getEventsStack().isNullOrEmpty()) {
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