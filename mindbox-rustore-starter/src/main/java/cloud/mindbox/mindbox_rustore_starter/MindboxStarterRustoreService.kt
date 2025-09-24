package cloud.mindbox.mindbox_rustore_starter

import cloud.mindbox.mindbox_rustore.MindboxRuStore
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import cloud.mindbox.mobile_sdk.Mindbox
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

public class MindboxStarterRustoreService : RuStoreMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Mindbox.updatePushToken(applicationContext, token, MindboxRuStore)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        MindboxCoreStarter.handleMindboxRemoteMessage(application, message)
    }
}
