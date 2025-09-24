package cloud.mindbox.mindbox_firebase_starter

import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

public class MindboxStarterFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        MindboxCoreStarter.onNewToken(applicationContext, token, MindboxFirebase)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        MindboxCoreStarter.onMessageReceived(application, remoteMessage)
    }
}
