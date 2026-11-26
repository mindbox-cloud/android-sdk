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
            SECOND_ACTIVITY_PUSH_URL to ActivityTransitionByPush::class.java
        )
        // Default Active. It will open if a link that is not in the list is received
        val defaultActivity = MainActivity::class.java

        val push = parseClientPush(message.data)
        if (push == null) {
            Log.d(Utils.TAG, "Push without messageId is not from the client gate, ignored")
            return
        }
        Log.d(Utils.TAG, push.toString())
        // If you want to save the notification you can call your save function from here.
        NotificationStorage.addNotification(push)

        showPushNotification(
            context = applicationContext,
            push = push,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription,
            pushSmallIcon = pushSmallIcon,
            activities = activities,
            defaultActivity = defaultActivity,
        )
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
