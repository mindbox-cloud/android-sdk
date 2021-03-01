package cloud.mindbox.mobile_sdk.services

import cloud.mindbox.mobile_sdk.MindboxLogger
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.paperdb.Paper

class MindboxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Mindbox.updateFmsToken(applicationContext, token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        //todo add parse message and getting uniqKey
        Mindbox.onPushReceived(applicationContext, "")
    }
}