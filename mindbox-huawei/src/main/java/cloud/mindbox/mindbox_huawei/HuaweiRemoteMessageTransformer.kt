package cloud.mindbox.mindbox_huawei

import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.huawei.hms.push.RemoteMessage as HuaweiRemoteMessage
import com.google.gson.Gson
import cloud.mindbox.mobile_sdk.pushes.PushAction as MindboxPushAction

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
            pushActions = parsedMessage.buttons.map(::pushAction),
            pushLink = parsedMessage.clickUrl,
            imageUrl = parsedMessage.imageUrl,
        )
    }

    private fun pushAction(action: PushAction) = MindboxPushAction(
        uniqueKey = action.uniqueKey,
        text = action.text,
        url = action.url,
    )

}