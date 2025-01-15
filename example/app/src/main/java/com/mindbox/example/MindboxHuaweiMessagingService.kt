package com.mindbox.example


import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mobile_sdk.Mindbox
import com.huawei.hms.push.RemoteMessage
import com.huawei.hms.push.HmsMessageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.mindbox.example.schedulePush.handleMindboxRemoteMessage

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
        coroutineScope.launch {
            if (MindboxHuawei.isMindboxPush(message)) {
                handleMindboxRemoteMessage(applicationContext, MindboxHuawei.convertToMindboxRemoteMessage(message))
            } else {
                // Handle other push notifications
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
