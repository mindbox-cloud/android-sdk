package cloud.mindbox.mindbox_huawei

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.huawei.hms.push.RemoteMessage

private typealias MindboxPushAction = cloud.mindbox.mobile_sdk.pushes.PushAction

/**
 * An object to use when choosing push provider in Mindbox.initPushServices or Mindbox.init.
 * Represents HCM
 * */
public object MindboxHuawei : MindboxPushService {

    private val gson by lazy { Gson() }

    override val tag: String = "HCM"

    override fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler = HuaweiServiceHandler(logger, exceptionHandler)

    /**
     * Checks if [RemoteMessage.getDataOfMap] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/

    public fun isMindboxPush(data: Map<String, String>): Boolean {
        return runCatching { convertToMindboxRemoteMessage(data) }.getOrNull() != null
    }

    /**
     * Checks if [RemoteMessage.getData] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/

    public fun isMindboxPush(data: String): Boolean {
        return runCatching { convertToMindboxRemoteMessage(data) }.getOrNull() != null
    }

    /**
     * Checks if [RemoteMessage] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/

    public fun isMindboxPush(remoteMessage: RemoteMessage?): Boolean {
        return remoteMessage?.data?.let { isMindboxPush(it) } ?: false
    }

    /**
     * Converts [RemoteMessage] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/

    public fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
        return remoteMessage?.data?.let { convertToMindboxRemoteMessage(it) }
    }

    /**
     * Converts [RemoteMessage.getDataOfMap] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/

    public fun convertToMindboxRemoteMessage(pushData: Map<String, String>?): MindboxRemoteMessage? {
        val data = pushData ?: return null
        val uniqueKey = data[HuaweiMessage.DATA_UNIQUE_KEY] ?: return null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        return MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = data[HuaweiMessage.DATA_TITLE] ?: "",
            description = data[HuaweiMessage.DATA_MESSAGE] ?: "",
            pushActions = runCatching {
                gson.fromJson<List<PushAction>?>(
                    data[HuaweiMessage.DATA_BUTTONS],
                    pushActionsType
                )
            }.getOrDefault(
                emptyList()
            ),
            pushLink = data[HuaweiMessage.DATA_PUSH_CLICK_URL],
            imageUrl = data[HuaweiMessage.DATA_IMAGE_URL],
            payload = data[HuaweiMessage.DATA_PAYLOAD],
        )
    }

    /**
     * Converts [RemoteMessage.getData] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/
    public fun convertToMindboxRemoteMessage(pushData: String?): MindboxRemoteMessage? {
        val data = pushData ?: return null
        val parsedMessage = gson.fromJson(data, HuaweiMessage::class.java) ?: return null
        return MindboxRemoteMessage(
            uniqueKey = parsedMessage.uniqueKey,
            title = parsedMessage.title ?: "",
            description = parsedMessage.message,
            pushActions = parsedMessage.buttons?.map { pushAction ->
                MindboxPushAction(
                    uniqueKey = pushAction.uniqueKey,
                    text = pushAction.text,
                    url = pushAction.url,
                )
            } ?: emptyList(),
            pushLink = parsedMessage.clickUrl,
            imageUrl = parsedMessage.imageUrl,
            payload = parsedMessage.payload,
        )
    }

    override fun toString(): String = tag
}
