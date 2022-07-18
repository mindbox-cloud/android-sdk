package cloud.mindbox.mobile_sdk.services

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.pushes.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson

internal class MindboxNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = LoggingExceptionHandler.runCatchingSuspending(
        defaultValue = Result.failure(),
    ) {
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, EMPTY_INT)
        require(notificationId != EMPTY_INT) { "Empty notification Id" }

        val message: RemoteMessage? = inputData.getString(KEY_REMOTE_MESSAGE)?.deserialize()
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
                    key to Class.forName(value) as Class<out Activity>
                }
            }
            ?.toMap()

        val defaultActivity = inputData.getString(KEY_ACTIVITY_DEFAULT)?.let {
            LoggingExceptionHandler.runCatching(defaultValue = null) {
                Class.forName(it) as Class<out Activity>
            }
        }
        requireNotNull(defaultActivity) { "defaultActivity is null" }

        val state: MessageHandlingState? = inputData.getString(KEY_STATE)?.deserialize()
        requireNotNull(state) { "State is null" }

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
            state = state.copy(attemptNumber = state.attemptNumber + 1),
        )
        Result.success()
    }


    companion object {

        private const val EMPTY_INT = 0

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

        private fun <T : Any> T.serialize(): String? {
            return LoggingExceptionHandler.runCatching(defaultValue = null) {
                gson.toJson(this)
            }
        }

        private inline fun <reified T : Any> String.deserialize(): T? {
            return LoggingExceptionHandler.runCatching(defaultValue = null) {
                gson.fromJson(this, T::class.java)
            }
        }

        fun inputData(
            notificationId: Int,
            remoteMessage: RemoteMessage,
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

}