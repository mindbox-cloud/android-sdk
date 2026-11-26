package cloud.mindbox.mobile_sdk.services

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.concurrent.TimeUnit

internal object PushDeliveryWorkManager {

    private val WORKER_BACKOFF = Milliseconds(10_000L)

    private val WORKER_TAG =
        "MindboxPushDeliveryWorkManager-${MindboxPreferences.hostAppName}"

    private val QUEUE_NAME = "$WORKER_TAG-queue"

    fun enqueue(context: Context, deliveryId: Long): Unit = loggingRunCatching {
        val builder = requestBuilder(MindboxPushDeliveryWorker.inputData(deliveryId))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager
            .getInstance(context)
            .beginUniqueWork("$WORKER_TAG-$deliveryId", ExistingWorkPolicy.KEEP, builder.build())
            .enqueue()
    }

    fun enqueueQueue(context: Context, delay: Milliseconds = Milliseconds(0L), throttles: Int = 0) =
        enqueueQueue(context, delay, throttles, ExistingWorkPolicy.KEEP)

    fun enqueueNextQueue(context: Context, delay: Milliseconds = Milliseconds(0L), throttles: Int = 0) =
        enqueueQueue(context, delay, throttles, ExistingWorkPolicy.APPEND_OR_REPLACE)

    fun enqueueAll(context: Context): Unit = loggingRunCatching {
        DbManager.getPushDeliveryIdsOutOfQueue().forEach { deliveryId -> enqueue(context, deliveryId) }
        if (DbManager.getQueuedPushDeliveries().isNotEmpty()) enqueueQueue(context)
    }

    fun cancelAllWork(context: Context): Unit = loggingRunCatching {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG)
    }

    private fun enqueueQueue(
        context: Context,
        delay: Milliseconds,
        throttles: Int,
        existingWorkPolicy: ExistingWorkPolicy,
    ): Unit = loggingRunCatching {
        val request = requestBuilder(MindboxPushDeliveryWorker.queueInputData(throttles))
            .setInitialDelay(delay.interval, TimeUnit.MILLISECONDS)
            .build()
        WorkManager
            .getInstance(context)
            .beginUniqueWork(QUEUE_NAME, existingWorkPolicy, request)
            .enqueue()
    }

    private fun requestBuilder(input: Data): OneTimeWorkRequest.Builder =
        OneTimeWorkRequestBuilder<MindboxPushDeliveryWorker>()
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WORKER_BACKOFF.interval, TimeUnit.MILLISECONDS)
            .addTag(WORKER_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
}
