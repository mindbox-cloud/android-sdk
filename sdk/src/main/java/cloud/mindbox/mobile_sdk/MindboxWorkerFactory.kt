package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.services.MindboxOneTimeEventWorker
import cloud.mindbox.mobile_sdk.services.MindboxPushDeliveryWorker

internal object MindboxWorkerFactory : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        MindboxOneTimeEventWorker::class.java.name -> MindboxOneTimeEventWorker(appContext, workerParameters)
        MindboxPushDeliveryWorker::class.java.name -> MindboxPushDeliveryWorker(appContext, workerParameters)
        else -> null
    }
}
