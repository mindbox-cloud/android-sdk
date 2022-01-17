package cloud.mindbox.mobile_sdk_core.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk_core.logOnException
import cloud.mindbox.mobile_sdk_core.managers.WorkerDelegate
import cloud.mindbox.mobile_sdk_core.returnOnException

internal class MindboxOneTimeEventWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    private val workerDelegate: WorkerDelegate by lazy { WorkerDelegate() }

    override fun doWork(): Result {
        return runCatching {
            return workerDelegate.sendEventsWithResult(
                context = applicationContext,
                parent = this
            )
        }.returnOnException { Result.failure() }
    }

    override fun onStopped() {
        super.onStopped()
        runCatching {
            workerDelegate.onEndWork(this)
        }.logOnException()
    }
}