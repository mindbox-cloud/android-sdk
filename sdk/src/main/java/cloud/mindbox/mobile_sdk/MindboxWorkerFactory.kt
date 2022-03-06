package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.services.MindboxOneTimeEventWorker

/**
 * Factory for custom initialisation of WorkManager
 *
 * You don't need this if you are using default WorkManager initialisation
 *
 * If you disabled automatic initialisation, add this factory to your DelegatingWorkerFactory
 * in place, where you register your factories
 *
 * Example:
 *
 * override fun getWorkManagerConfiguration() = Configuration.Builder()
 *     .setWorkerFactory(
 *         DelegatingWorkerFactory().apply {
 *             // your factories
 *             addFactory(MindboxWorkerFactory) // Mindbox factory
 *         }
 *      )
 *     .build()
 */
object MindboxWorkerFactory : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = workerClassName
        .takeIf { it == MindboxOneTimeEventWorker::class.qualifiedName }
        ?.let { MindboxOneTimeEventWorker(appContext, workerParameters) }

}