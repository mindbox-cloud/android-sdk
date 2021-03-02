package cloud.mindbox.mobile_sdk.services

import cloud.mindbox.mobile_sdk.Mindbox
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal class MindboxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Mindbox.updateFmsToken(applicationContext, token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        //todo add parse message and getting uniqKey
        Mindbox.onPushReceived(applicationContext, "")
    }
}