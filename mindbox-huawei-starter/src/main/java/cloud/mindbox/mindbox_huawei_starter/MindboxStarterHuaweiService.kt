package cloud.mindbox.mindbox_huawei_starter

import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

public class MindboxStarterHuaweiService : HmsMessageService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Mindbox.updatePushToken(applicationContext, token, MindboxHuawei)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val notificationConfig = runCatching { MindboxCoreStarter.getNotificationData(application) }.getOrElse { exception ->
            Mindbox.writeLog(exception.message.toString(), Level.ERROR)
            return
        }
        coroutineScope.launch {
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
}
