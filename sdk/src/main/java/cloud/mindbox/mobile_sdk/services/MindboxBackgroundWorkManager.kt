package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.returnOnException
import java.util.concurrent.TimeUnit

internal object BackgroundWorkManager {

    private val WORKER_TAG =
        "MindboxBackgroundWorkManager${MindboxPreferences.hostAppName}"

    fun startPeriodicService(context: Context) {
        runCatching {
            val request = PeriodicWorkRequest.Builder(
                MindboxPeriodicEventWorker::class.java,
                15, TimeUnit.MINUTES
            )
                .setInitialDelay(15, TimeUnit.MINUTES)
                .addTag(WORKER_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

        }.returnOnException { }
    }

    fun startOneTimeService(context: Context) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<MindboxOneTimeEventWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .addTag(WORKER_TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()

            WorkManager
                .getInstance(context)
                .beginUniqueWork(
                    WORKER_TAG,
                    ExistingWorkPolicy.KEEP,
                    request
                )
                .enqueue()

        }.returnOnException { }
    }
}

internal enum class WorkerType {
    ONE_TIME_WORKER, PERIODIC_WORKER
}