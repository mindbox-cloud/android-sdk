package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.managers.logEndWork
import cloud.mindbox.mobile_sdk.managers.sendEventsWithResult
import cloud.mindbox.mobile_sdk.returnOnException

internal class MindboxOneTimeEventWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return runCatching {
            return sendEventsWithResult(
                context = applicationContext,
                parent = this,
                workerType = WorkerType.ONE_TIME_WORKER
            )
        }.returnOnException { Result.failure() }
    }

    override fun onStopped() {
        super.onStopped()
        runCatching {
            logEndWork(this)
        }.logOnException()
    }
}