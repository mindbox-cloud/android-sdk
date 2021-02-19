package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import java.util.concurrent.TimeUnit

internal object BackgroundWorkManager {

    private val ONE_TIME_WORKER_TAG = MindboxOneTimeEventWorker::class.java.simpleName + MindboxPreferences.hostAppName

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
                .addTag(ONE_TIME_WORKER_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()

            WorkManager
                .getInstance(context)
                .beginUniqueWork(
                    ONE_TIME_WORKER_TAG,
                    ExistingWorkPolicy.KEEP,
                    request
                )
                .enqueue()
    }

    fun stopOneTimeService(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(ONE_TIME_WORKER_TAG)
    }
}

internal enum class WorkerType {
    ONE_TIME_WORKER, PERIODIC_WORKER
}