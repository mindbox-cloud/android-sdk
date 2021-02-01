package cloud.mindbox.mobile_sdk.services

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.paperdb.Paper

class MindboxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (token.isNotEmpty() && token != MindboxPreferences.firebaseToken) {
            Paper.init(applicationContext)
            if (DbManager.getConfigurations() == null) return //todo change init db
            Mindbox.init(applicationContext, DbManager.getConfigurations()!!)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {

    }
}