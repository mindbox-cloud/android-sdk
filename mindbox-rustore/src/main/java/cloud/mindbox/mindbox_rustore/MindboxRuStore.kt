package cloud.mindbox.mindbox_rustore

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage

@Suppress("unused")
fun MindboxRuStore(projectId: String): MindboxRuStore = MindboxRuStoreInternal.apply {
    this.projectId = projectId
}

/***
 * Use factory function MindboxRuStore(projectId) to create an instance of this class.
 */
abstract class MindboxRuStore : MindboxPushService by MindboxRuStoreInternal {

    companion object {
        /**
         * Checks if [RemoteMessage] is sent with Mindbox
         * Returns true if it is or false otherwise
         **/
        fun isMindboxPush(remoteMessage: RemoteMessage): Boolean {
            return runCatching { convertToMindboxRemoteMessage(remoteMessage) }.getOrNull() != null
        }

        /**
         * Converts [RemoteMessage] to [MindboxRemoteMessage]
         * Use this method to get mindbox push-notification data
         * It is encouraged to use this method inside try/catch block
         * @throws JsonSyntaxException – if remote message can't be parsed
         **/
        fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
            return MindboxRuStoreInternal.convertToMindboxRemoteMessage(remoteMessage)
        }
    }

    var projectId: String = ""
        internal set(value) = run { field = value }
}

internal object MindboxRuStoreInternal : MindboxRuStore() {

    override val tag: String = "RuStore"

    override fun toString(): String = tag

    private val gson by lazy { Gson() }

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler {
        return RuStoreServiceHandler(logger, exceptionHandler, projectId)
    }

    fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
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
