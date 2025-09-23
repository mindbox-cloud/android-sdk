package cloud.mindbox.mindbox_firebase_starter

import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

public class MindboxStarterFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Mindbox.updatePushToken(applicationContext, token, MindboxFirebase)
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
