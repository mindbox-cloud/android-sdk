package com.mindbox.example

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService

class MindboxFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("Test", "On new token: $token")
    }
}