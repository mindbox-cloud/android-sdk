package cloud.mindbox.mindbox_huawei

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.gson.Gson
import com.huawei.hms.push.RemoteMessage

private typealias MindboxPushAction = cloud.mindbox.mobile_sdk.pushes.PushAction

/**
 * An object to use when choosing push provider in Mindbox.initPushServices or Mindbox.init.
 * Represents HCM
 * */
object MindboxHuawei : MindboxPushService {

    private val gson by lazy { Gson() }

    override val tag: String = "HCM"

    override fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler = HuaweiServiceHandler(logger, exceptionHandler)

    /**
     * Checks if [RemoteMessage] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/
    fun isMindboxPush(remoteMessage: RemoteMessage?): Boolean {
        return runCatching { convertToMindboxRemoteMessage(remoteMessage) }.getOrNull() != null
    }

    /**
     * Converts [RemoteMessage] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/
    fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
        val data = remoteMessage?.data ?: return null
        val parsedMessage = gson.fromJson(data, HuaweiMessage::class.java) ?: return null
        return MindboxRemoteMessage(
            uniqueKey = parsedMessage.uniqueKey,
            title = parsedMessage.title ?: "",
            description = parsedMessage.message,
            pushActions = parsedMessage.buttons.map { pushAction ->
                MindboxPushAction(
                    uniqueKey = pushAction.uniqueKey,
                    text = pushAction.text,
                    url = pushAction.url,
                )
            },
            pushLink = parsedMessage.clickUrl,
            imageUrl = parsedMessage.imageUrl,
            payload = parsedMessage.payload,
        )
    }

    override fun toString(): String = tag
}
