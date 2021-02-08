package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.EventManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import io.paperdb.Paper
import java.lang.Exception
import java.util.concurrent.CountDownLatch

internal class MindboxPeriodicEventWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams)  {

    override fun doWork(): Result {
        Logger.d(this, "Start periodic working...")

        try {
            Paper.init(applicationContext)

            val eventKeys = DbManager.getEventsKeys()
            if (eventKeys.isNullOrEmpty()) {
                Logger.d(this, "Events list is empty")
                Result.success()
            } else {

                EventManager.sendEvents(applicationContext, eventKeys)

                return if (!DbManager.getEventsKeys().isNullOrEmpty()) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Logger.e(this, "Failed periodic events work", e)
            Result.failure()
        }
        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        Logger.d(this, "onStopped periodic work")
    }
}