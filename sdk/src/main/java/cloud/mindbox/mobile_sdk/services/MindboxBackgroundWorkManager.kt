package cloud.mindbox.mobile_sdk.services

import android.app.Activity
import android.content.Context
import androidx.work.*
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.util.concurrent.TimeUnit

internal object BackgroundWorkManager {

    private const val INITIAL_DELAY = 120L

    private val NOTIFICATION_WORKER_TAG =
        "MindboxNotificationWorkManager-${MindboxPreferences.hostAppName}"

    private val WORKER_TAG =
        "MindboxBackgroundWorkManager-${MindboxPreferences.hostAppName}"

    fun startOneTimeService(context: Context) = LoggingExceptionHandler.runCatching {
        val request = OneTimeWorkRequestBuilder<MindboxOneTimeEventWorker>()
            .setInitialDelay(INITIAL_DELAY, TimeUnit.SECONDS)
            .addTag(WORKER_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager
            .getInstance(context)
            .beginUniqueWork(WORKER_TAG, ExistingWorkPolicy.KEEP, request)
            .enqueue()
    }

    fun cancelAllWork(context: Context) = WorkManager.getInstance(context)
        .cancelAllWorkByTag(WORKER_TAG)

    fun startNotificationWork(
        context: Context,
        notificationId: Int,
        mindboxRemoteMessage: MindboxRemoteMessage,
        channelId: String,
        channelName: String,
        pushSmallIcon: Int,
        channelDescription: String?,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        delay: Long,
        state: MessageHandlingState,
    ) = LoggingExceptionHandler.runCatching {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data = MindboxNotificationWorker.inputData(
            mindboxRemoteMessage = mindboxRemoteMessage,
            channelId = channelId,
            channelName = channelName,
            pushSmallIcon = pushSmallIcon,
            channelDescription = channelDescription,
            activities = activities,
            defaultActivity = defaultActivity,
            notificationId = notificationId,
            state = state,
        )
        val request = OneTimeWorkRequestBuilder<MindboxNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(NOTIFICATION_WORKER_TAG)
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(
                MindboxNotificationWorker.defaultBackoffPolicy,
                MindboxNotificationWorker.defaultBackoffDelayMillis,
                TimeUnit.MILLISECONDS,
            )
            .build()
        val uniqueName = getUniqueWorkerNameFor(notificationId)
        WorkManager
            .getInstance(context)
            .beginUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
            .enqueue()
    }

    private fun getUniqueWorkerNameFor(id: Int): String = "$NOTIFICATION_WORKER_TAG-$id"

}
