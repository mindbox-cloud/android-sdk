package com.mindbox.example

import android.util.Log
import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mobile_sdk.Mindbox
import com.huawei.hms.push.RemoteMessage
import com.huawei.hms.push.HmsMessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MindboxHuaweiMessagingService : HmsMessageService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // Override resource mindbox_default_notification_color to change color pushSmallIcon
        // On some devices, onMessageReceived may be executed on the main thread
        // We recommend handling push messages asynchronously
        coroutineScope.launch {
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
            val mindboxMessage =
                MindboxHuawei.convertToMindboxRemoteMessage(remoteMessage = message)
            Log.d(Utils.TAG, mindboxMessage.toString())
            // If you want to save the notification you can call your save function from here.

            if (!messageWasHandled) {
                // If the push notification was not from Mindbox or it contains incorrect data, you can write a fallback to process it.
                Log.d(Utils.TAG, "This push not from Mindbox")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
