package cloud.mindbox.mindbox_huawei

import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.huawei.hms.push.RemoteMessage as HuaweiRemoteMessage

internal class HuaweiRemoteMessageTransformer(private val exceptionHandler: ExceptionHandler) {

    fun transform(
        remoteMessage: HuaweiRemoteMessage?,
    ): MindboxRemoteMessage? = exceptionHandler.runCatching(defaultValue = null) {
        MindboxHuawei.convertToMindboxRemoteMessage(remoteMessage)
    }
}