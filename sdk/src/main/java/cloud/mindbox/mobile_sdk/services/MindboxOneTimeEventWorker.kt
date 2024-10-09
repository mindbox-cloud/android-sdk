package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.managers.WorkerDelegate
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal class MindboxOneTimeEventWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    private val workerDelegate: WorkerDelegate by lazy { WorkerDelegate() }

    override fun doWork(): Result = LoggingExceptionHandler.runCatching(
        defaultValue = Result.failure()
    ) {
        workerDelegate.sendEventsWithResult(
            context = applicationContext,
            parent = this
        )
    }

    override fun onStopped() {
        super.onStopped()
        LoggingExceptionHandler.runCatching {
            workerDelegate.onEndWork(this)
        }
    }
}
