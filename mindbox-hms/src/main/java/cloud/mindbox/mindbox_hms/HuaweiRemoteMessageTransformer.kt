package cloud.mindbox.mindbox_hms

import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.huawei.hms.push.RemoteMessage as HuaweiRemoteMessage
import com.google.gson.Gson

internal class HuaweiRemoteMessageTransformer(private val exceptionHandler: ExceptionHandler) {

    private val gson by lazy { Gson() }

    fun transform(
        remoteMessage: HuaweiRemoteMessage?,
    ): RemoteMessage? = exceptionHandler.runCatching(defaultValue = null) {
        val data = remoteMessage?.data ?: return@runCatching null
        val parsedMessage = gson.fromJson(data, HuaweiMessage::class.java)
        RemoteMessage(
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