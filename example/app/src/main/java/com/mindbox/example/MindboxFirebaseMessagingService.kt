package com.mindbox.example

import android.util.Log
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.firebase.messaging.FirebaseMessagingService

class MindboxFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("Test", "On new token: $token")
        Mindbox.updatePushToken(applicationContext, token, MindboxFirebase)
    }
}