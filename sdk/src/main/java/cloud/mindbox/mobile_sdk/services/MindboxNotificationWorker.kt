package cloud.mindbox.mobile_sdk.services

import android.app.Activity
import android.content.Context
import androidx.work.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson

internal class MindboxNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        val defaultBackoffPolicy = BackoffPolicy.EXPONENTIAL
        val defaultBackoffDelayMillis = WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS

        private const val EMPTY_INT = 0

        private const val MAX_RETRY_COUNT = 10

        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val KEY_REMOTE_MESSAGE = "remote_message"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_SMALL_ICON_RES = "small_icon_res"
        private const val KEY_CHANNEL_DESCRIPTION = "channel_description"
        private const val KEY_ACTIVITIES = "activities"
        private const val KEY_ACTIVITY_DEFAULT = "activity_default"
        private const val KEY_STATE = "state"

        private val gson = Gson()

        private fun <T> T.serialize(): String? = LoggingExceptionHandler.runCatching(
            defaultValue = null,
        ) {
            gson.toJson(this)
        }

        private inline fun <reified T> String.deserialize() = LoggingExceptionHandler.runCatching(
            defaultValue = null,
        ) {
            gson.fromJson(this, T::class.java)
        }

        fun inputData(
            notificationId: Int,
            remoteMessage: MindboxRemoteMessage,
            channelId: String,
            channelName: String,
            pushSmallIcon: Int,
            channelDescription: String?,
            activities: Map<String, Class<out Activity>>?,
            defaultActivity: Class<out Activity>,
            state: MessageHandlingState,
        ): Data {
            val messageString: String? = remoteMessage.serialize()
            val activitiesString: String? = activities?.mapValues { it.value.canonicalName }?.serialize()
            val defaultActivityString: String? = defaultActivity.canonicalName
            val stateString: String? = state.serialize()

            return Data.Builder()
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .putString(KEY_REMOTE_MESSAGE, messageString)
                .putString(KEY_CHANNEL_ID, channelId)
                .putString(KEY_CHANNEL_NAME, channelName)
                .putInt(KEY_SMALL_ICON_RES, pushSmallIcon)
                .putString(KEY_CHANNEL_DESCRIPTION, channelDescription)
                .putString(KEY_ACTIVITIES, activitiesString)
                .putString(KEY_ACTIVITY_DEFAULT, defaultActivityString)
                .putString(KEY_STATE, stateString)
                .build()
        }

    }

    override suspend fun doWork(): Result = LoggingExceptionHandler.runCatchingSuspending(
        defaultValue = Result.failure(),
    ) {
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, EMPTY_INT)
        require(notificationId != EMPTY_INT) { "Empty notification Id" }

        val message = inputData.getString(KEY_REMOTE_MESSAGE)?.deserialize<MindboxRemoteMessage>()
        requireNotNull(message) { "RemoteMessage is null" }

        val channelId = inputData.getString(KEY_CHANNEL_ID)
        requireNotNull(channelId) { "channelId is null" }

        val channelName = inputData.getString(KEY_CHANNEL_NAME)
        requireNotNull(channelName) { "channelName is null" }

        val pushSmallIcon = inputData.getInt(KEY_SMALL_ICON_RES, EMPTY_INT)
        require(notificationId != EMPTY_INT) { "Empty pushSmallIcon" }

        val channelDescription = inputData.getString(KEY_CHANNEL_DESCRIPTION)

        val activities = inputData.getString(KEY_ACTIVITIES)
            ?.deserialize<Map<String, String>>()
            ?.mapNotNull { (key, value) ->
                LoggingExceptionHandler.runCatching(defaultValue = null) {
                    @Suppress("UNCHECKED_CAST")
                    key to Class.forName(value) as Class<out Activity>
                }
            }
            ?.toMap()

        val defaultActivity = inputData.getString(KEY_ACTIVITY_DEFAULT)?.let {
            LoggingExceptionHandler.runCatching(defaultValue = null) {
                @Suppress("UNCHECKED_CAST")
                Class.forName(it) as Class<out Activity>
            }
        }
        requireNotNull(defaultActivity) { "defaultActivity is null" }

        val state: MessageHandlingState? = inputData.getString(KEY_STATE)?.deserialize()
        requireNotNull(state) { "State is null" }

        try {
            //Under normal conditions, everything should start successfully,
            //but still, if something goes wrong, it's worth trying to start again
            PushNotificationManager.tryNotifyRemoteMessage(
                context = this.applicationContext,
                remoteMessage = message,
                channelId = channelId,
                channelName = channelName,
                pushSmallIcon = pushSmallIcon,
                channelDescription = channelDescription,
                activities = activities,
                defaultActivity = defaultActivity,
                notificationId = notificationId,
                state = state.copy(attemptNumber = state.attemptNumber + 1 + runAttemptCount),
            )
            Result.success()
        } catch (e: Throwable) {
            if (runAttemptCount >= MAX_RETRY_COUNT) {
                MindboxLoggerImpl.e(
                    parent = PushNotificationManager,
                    message = PushNotificationManager.buildLogMessage(
                        message = message,
                        log = "Failed:",
                    ),
                    exception = e,
                )
                Result.failure()
            } else {
                MindboxLoggerImpl.e(
                    parent = PushNotificationManager,
                    message = PushNotificationManager.buildLogMessage(
                        message = message,
                        log = "Failed, retry scheduled:",
                    ),
                    exception = e,
                )
                Result.retry()
            }
        }

    }

}