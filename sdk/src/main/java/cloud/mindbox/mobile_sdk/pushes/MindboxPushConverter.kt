package cloud.mindbox.mobile_sdk.pushes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * An interface for internal sdk work only. Do not implement it
 * */
public abstract class MindboxPushConverter<in RemoteMessage> {

    private val gson by lazy { Gson() }

    /**
     * Gets push data [RemoteMessage.data] as [Map]
     **/
    protected abstract fun RemoteMessage.pushData(): Map<String, String>

    /**
     * Checks if [RemoteMessage] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/
    public fun isMindboxPush(remoteMessage: RemoteMessage): Boolean {
        return isMindboxPush(remoteMessage.pushData())
    }

    /**
     * Checks if [RemoteMessage.data] is sent with Mindbox
     * Returns true if it is or false otherwise
     **/
    public fun isMindboxPush(data: Map<String, String>): Boolean {
        return runCatching { convertToMindboxRemoteMessage(data) }.getOrNull() != null
    }

    /**
     * Converts [RemoteMessage] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/
    public fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
        return remoteMessage?.pushData()?.let { convertToMindboxRemoteMessage(it) }
    }

    /**
     * Converts [RemoteMessage.data] to [MindboxRemoteMessage]
     * Use this method to get mindbox push-notification data
     * It is encouraged to use this method inside try/catch block
     * @throws JsonSyntaxException – if remote message can't be parsed
     **/
    public fun convertToMindboxRemoteMessage(data: Map<String, String>): MindboxRemoteMessage? {
        val uniqueKey = data[MindboxRemoteMessage.DATA_UNIQUE_KEY] ?: return null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        return MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = data[MindboxRemoteMessage.DATA_TITLE] ?: "",
            description = data[MindboxRemoteMessage.DATA_MESSAGE] ?: "",
            pushActions = runCatching {
                gson.fromJson<List<PushAction>?>(
                    data[MindboxRemoteMessage.DATA_BUTTONS],
                    pushActionsType
                )
            }.getOrDefault(
                emptyList()
            ),
            pushLink = data[MindboxRemoteMessage.DATA_PUSH_CLICK_URL],
            imageUrl = data[MindboxRemoteMessage.DATA_IMAGE_URL],
            payload = data[MindboxRemoteMessage.DATA_PAYLOAD],
        )
    }
}
