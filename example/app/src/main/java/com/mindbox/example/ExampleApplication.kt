package com.mindbox.example

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

class ExampleApp : Application() {
    companion object {
        private var privateApplication: Application? = null
        val application: Application
            get() = privateApplication!!
    }

    override fun onCreate() {
        super.onCreate()
        privateApplication = this
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("Test", "The token was received $token")
            }
    }
}