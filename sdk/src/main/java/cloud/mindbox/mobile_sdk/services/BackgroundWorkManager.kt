package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

internal object BackgroundWorkManager {

    fun startPeriodicService(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            MindboxPeriodicEventWorker::class.java,
            15, TimeUnit.MINUTES
        )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                60 * 1000, // 60 sec
                TimeUnit.MILLISECONDS
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MindboxPeriodicEventWorker::class.java.simpleName,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun startOneTimeService(context: Context) {
            val request = OneTimeWorkRequestBuilder<MindboxOneTimeEventWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()

            WorkManager
                .getInstance(context)
                .beginUniqueWork(
                    MindboxOneTimeEventWorker::class.java.simpleName,
                    ExistingWorkPolicy.KEEP,
                    request
                )
                .enqueue()
    }
}

internal enum class WorkerType {
    ONE_TIME_WORKER, PERIODIC_WORKER
}