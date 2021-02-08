package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.EventManager
import io.paperdb.Paper
import java.lang.Exception

class MindboxOneTimeEventWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams)  {
    override fun doWork(): Result {
        Logger.d(this, "Start one-time working...")

        try {
            Paper.init(applicationContext)

            val eventKeys = DbManager.getEventsKeys()
            if (eventKeys.isNullOrEmpty()) {
                Logger.d(this, "Events list is empty")
                Result.success()
            } else {

                EventManager.sendEvents(applicationContext, eventKeys)

                return Result.success()
            }
        } catch (e: Exception) {
            Logger.e(this, "Failed one-time events work", e)
            Result.failure()
        }
        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        Logger.d(this, "onStopped one-time work")
    }
}