package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.services.MindboxOneTimeEventWorker

internal object MindboxWorkerFactory : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = workerClassName
        .takeIf { it == MindboxOneTimeEventWorker::class.qualifiedName }
        ?.let { MindboxOneTimeEventWorker(appContext, workerParameters) }

}