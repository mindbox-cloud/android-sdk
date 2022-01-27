package cloud.mindbox.mindbox_hms

import cloud.mindbox.mobile_sdk_core.pushes.PushAction
import cloud.mindbox.mobile_sdk_core.pushes.RemoteMessage
import com.huawei.hms.push.RemoteMessage as HuaweiRemoteMessage
import com.google.gson.Gson

internal object HuaweiRemoteMessageTransformer {

    private val gson by lazy { Gson() }

    fun transform(remoteMessage: HuaweiRemoteMessage?): RemoteMessage? {
        val data = remoteMessage?.data ?: return null
        val parsedMessage = gson.fromJson(data, HuaweiMessage::class.java)
        return RemoteMessage(
            uniqueKey = parsedMessage.uniqueKey,
            title = parsedMessage.title,
            description = parsedMessage.message,
            pushActions = parsedMessage.buttons.map {
                PushAction(
                    uniqueKey = it.uniqueKey,
                    text = it.text,
                    url = it.url,
                )
            },
            pushLink = parsedMessage.clickUrl,
            imageUrl = parsedMessage.imageUrl,
        )
    }

}