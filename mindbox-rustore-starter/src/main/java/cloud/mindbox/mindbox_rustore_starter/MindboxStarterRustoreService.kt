package cloud.mindbox.mindbox_rustore_starter

import cloud.mindbox.mindbox_rustore.MindboxRuStore
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

public class MindboxStarterRustoreService : RuStoreMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Mindbox.updatePushToken(applicationContext, token, MindboxRuStore)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val notificationConfig = runCatching { MindboxCoreStarter.getNotificationData(application) }.getOrElse { exception ->
            Mindbox.writeLog(exception.message.toString(), Level.ERROR)
            return
        }
        notificationConfig.defaultActivity?.let { defaultActivity ->
            Mindbox.handleRemoteMessage(
                context = applicationContext,
                activities = notificationConfig.activities,
                message = remoteMessage,
                channelId = notificationConfig.channelId,
                channelName = notificationConfig.channelName,
                pushSmallIcon = notificationConfig.smallIcon,
                defaultActivity = defaultActivity,
                channelDescription = notificationConfig.channelDescription
            )
        }
    }
}
