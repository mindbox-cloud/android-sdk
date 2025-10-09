package cloud.mindbox.mindbox_huawei_starter

import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mindbox_sdk_starter_core.MindboxCoreStarter
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

public class MindboxStarterHuaweiService : HmsMessageService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        MindboxCoreStarter.onNewToken(applicationContext, token, MindboxHuawei)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        coroutineScope.launch {
            MindboxCoreStarter.onMessageReceived(application, remoteMessage)
        }
    }
}
