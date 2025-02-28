package cloud.mindbox.mindbox_rustore

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage

@Suppress("unused")
public fun MindboxRuStore(projectId: String): MindboxRuStore = MindboxRuStore.apply {
    this.projectId = projectId
}

/***
 * Use factory function MindboxRuStore(projectId) to set up RuStore projectId.
 */
public object MindboxRuStore : MindboxPushService {

    internal var projectId: String = ""
        internal set(value) = run { field = value }

    override val tag: String = "RuStore"

    override fun toString(): String = tag

    private val gson by lazy { Gson() }

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler {
        return RuStoreServiceHandler(logger, exceptionHandler, projectId)
    }

    /**
     * Checks if [RemoteMessage] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/
    public fun isMindboxPush(remoteMessage: RemoteMessage): Boolean {
        return runCatching { convertToMindboxRemoteMessage(remoteMessage) }.getOrNull() != null
    }

    /**
     * Converts [RemoteMessage] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/
    public fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
        val data = remoteMessage?.data ?: return null
        val uniqueKey = data[RuStoreMessage.DATA_UNIQUE_KEY] ?: return null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        return MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = data[RuStoreMessage.DATA_TITLE] ?: "",
            description = data[RuStoreMessage.DATA_MESSAGE] ?: "",
            pushActions = runCatching {
                gson.fromJson<List<PushAction>?>(
                    data[RuStoreMessage.DATA_BUTTONS],
                    pushActionsType
                )
            }.getOrDefault(
                emptyList()
            ),
            pushLink = data[RuStoreMessage.DATA_PUSH_CLICK_URL],
            imageUrl = data[RuStoreMessage.DATA_IMAGE_URL],
            payload = data[RuStoreMessage.DATA_PAYLOAD],
        )
    }
}
