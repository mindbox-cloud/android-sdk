package com.mindbox.example

import android.util.Log
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mobile_sdk.Mindbox
import com.huawei.hms.push.RemoteMessage
import com.huawei.hms.push.HmsMessageService

class MindboxHuaweiMessagingService : HmsMessageService() {

    override fun onNewToken(token: String) {
        // Token transfer to Mindbox SDK
        // https://developers.mindbox.ru/docs/android-sdk-methods#updatepushtoken
        Mindbox.updatePushToken(
            context = applicationContext,
            token = token,
            pushService = MindboxHuawei
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val channelId = "mindbox_app_channel"
        val channelName = "defaultChannelName"
        val channelDescription = "defaultDescription"
        val pushSmallIcon = R.mipmap.ic_launcher
        // Listing of links and activites that should be opened by different links
        val activities = mapOf(
            "https://newActivity.com" to ActivityTransitionByPush::class.java
        )
        // Default Active. It will open if a link that is not in the list is received
        val defaultActivity = MainActivity::class.java

        // Method for rendering mobile push from Mindbox
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
        val isMindboxPush = MindboxHuawei.isMindboxPush(remoteMessage = message)

        // Method for getting info from Mindbox push
        val mindboxMessage = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessage = message)
        Log.d(Utils.TAG, mindboxMessage.toString())

        if (!messageWasHandled) {
            // If the push notification was not from Mindbox or it contains incorrect data, you can write a fallback to process it.
            Log.d(Utils.TAG, "This push not from Mindbox")
        }
    }
}