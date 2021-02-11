package com.mindbox.androidsdk

import android.util.Log
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushokMessagingService: FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Mindbox.updateFmsToken(token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(p0: RemoteMessage) {
        Log.i("MINDBOX PUSH", p0.data.toString())
        Mindbox.onPushReceived(applicationContext, "from Pushok")
        super.onMessageReceived(p0)
    }
}