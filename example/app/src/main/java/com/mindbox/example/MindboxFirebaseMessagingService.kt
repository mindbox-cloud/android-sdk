package com.mindbox.example

import android.util.Log
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MindboxFirebaseMessagingService : FirebaseMessagingService() {
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
            pushService = MindboxFirebase
        )
    }
}