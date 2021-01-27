package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackgroundWorkManager {

    fun start(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            MindboxEventWorker::class.java,
            1, TimeUnit.HOURS
        ).setConstraints(
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