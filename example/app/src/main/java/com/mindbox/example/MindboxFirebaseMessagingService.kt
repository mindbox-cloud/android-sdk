package com.mindbox.example

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mindbox.example.schedulePush.handleMindboxRemoteMessage

class MindboxFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (MindboxFirebase.isMindboxPush(message)) {
            handleMindboxRemoteMessage(applicationContext, message)
        } else {
            // Handle other push notifications
        }
    }

    override fun onNewToken(token: String) {
        // Token transfer to Mindbox SDK
        // https://developers.mindbox.ru/docs/android-sdk-methods#updatepushtoken
        Mindbox.updatePushToken(
            context = applicationContext,
            token = token,
            pushService = MindboxFirebase
        )
    }
}
