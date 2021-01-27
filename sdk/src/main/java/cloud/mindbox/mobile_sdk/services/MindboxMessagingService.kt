package cloud.mindbox.mobile_sdk.services

import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MindboxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (token.isNotEmpty() && token != MindboxPreferences.firebaseToken) {
            MindboxPreferences.firebaseToken = token
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {

    }
}