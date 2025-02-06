package com.mindbox.example

import android.util.Log
import cloud.mindbox.mindbox_rustore.MindboxRuStore
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.ExampleApp.Companion.RU_STORE_PROJECT_ID
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

class MindboxRuStoreMessagingService : RuStoreMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        val channelId = "mindbox_app_channel"
        val channelName = "defaultChannelName"
        val channelDescription = "defaultDescription"
        val pushSmallIcon = R.mipmap.ic_launcher

        super.onMessageReceived(message)
        // Listing of links and activities that should be opened by different links
        val activities = mapOf(
            "https://newActivity.com" to ActivityTransitionByPush::class.java
        )
        // Default Active. It will open if a link that is not in the list is received
        val defaultActivity = MainActivity::class.java

        // Method for rendering mobile push from Mindbox
        // Override resource mindbox_default_notification_color to change color pushSmallIcon
        val messageWasHandled = Mindbox.handleRemoteMessage(
            context = applicationContext,
            message = message,
            activities = activities,
            channelId = channelId,
            channelName = channelName,
            pushSmallIcon = pushSmallIcon,
            defaultActivity = defaultActivity,
            channelDescription = channelDescription
        )

        // Method for checking if push is from Mindbox
        val isMindboxPush = MindboxRuStore.isMindboxPush(remoteMessage = message)

        // Method for getting info from Mindbox push
        val mindboxMessage = MindboxRuStore.convertToMindboxRemoteMessage(remoteMessage = message)
        Log.d(Utils.TAG, mindboxMessage.toString())
        // If you want to save the notification you can call your save function from here.
        mindboxMessage?.let {
            NotificationStorage.addNotification(it)
        }
        if (!messageWasHandled) {
            // If the push notification was not from Mindbox or it contains incorrect data, you can write a fallback to process it.
            Log.d(Utils.TAG, "This push not from Mindbox")
        }
    }

    override fun onNewToken(token: String) {
        // Token transfer to Mindbox SDK
        //https://developers.mindbox.ru/docs/android-sdk-methods#updatepushtoken
        Mindbox.updatePushToken(
            context = applicationContext,
            token = token,
            pushService = MindboxRuStore
        )
    }

    override fun onDeletedMessages() {
        Log.i("RuStore", "Deleted messages")
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        Log.i("RuStore", "Error: $errors")
    }
}
