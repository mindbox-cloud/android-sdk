package com.mindbox.example

import android.util.Log
import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mindbox_rustore.MindboxRuStore
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.ExampleApp.Companion.RU_STORE_PROJECT_ID
import com.mindbox.example.schedulePush.handleMindboxRemoteMessage
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

class MindboxRuStoreMessagingService : RuStoreMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (MindboxRuStore.isMindboxPush(message)) {
            handleMindboxRemoteMessage(applicationContext, MindboxRuStore.convertToMindboxRemoteMessage(message))
        } else {
            // Handle other push notifications
        }

    }

    override fun onNewToken(token: String) {
        // Token transfer to Mindbox SDK
        //https://developers.mindbox.ru/docs/android-sdk-methods#updatepushtoken
        Mindbox.updatePushToken(
            context = applicationContext,
            token = token,
            pushService = MindboxRuStore(projectId = RU_STORE_PROJECT_ID)
        )
    }

    override fun onDeletedMessages() {
        Log.i("RuStore", "Deleted messages")
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        Log.i("RuStore", "Error: $errors")
    }
}
