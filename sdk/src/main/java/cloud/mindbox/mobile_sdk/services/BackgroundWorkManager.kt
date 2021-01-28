package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackgroundWorkManager {

    fun start(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            MindboxEventWorker::class.java,
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
            MindboxEventWorker::class.java.simpleName,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}