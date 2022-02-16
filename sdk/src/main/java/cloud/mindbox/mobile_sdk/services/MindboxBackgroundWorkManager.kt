package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.util.concurrent.TimeUnit

internal object BackgroundWorkManager {

    private val WORKER_TAG =
        "MindboxBackgroundWorkManager${MindboxPreferences.hostAppName}"

    fun startOneTimeService(context: Context) {
        LoggingExceptionHandler.runCatching {
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

        }
    }
}
