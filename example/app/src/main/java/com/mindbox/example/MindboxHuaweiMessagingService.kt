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
            SECOND_ACTIVITY_PUSH_URL to ActivityTransitionByPush::class.java
        )
        // Default Active. It will open if a link that is not in the list is received
        val defaultActivity = MainActivity::class.java

        // On some devices, onMessageReceived may be executed on the main thread
        // We recommend handling push messages asynchronously
        coroutineScope.launch {
            val push = parseClientPush(message.dataOfMap)
            if (push == null) {
                Log.d(Utils.TAG, "Push without messageId is not from the client gate, ignored")
                return@launch
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
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}