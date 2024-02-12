package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * An object to use when choosing push provider in Mindbox.initPushServices or Mindbox.init.
 * Represents FCM
 * */
object MindboxFirebase : MindboxPushService {

    private const val DATA_UNIQUE_KEY = "uniqueKey"
    private const val DATA_TITLE = "title"
    private const val DATA_MESSAGE = "message"
    private const val DATA_IMAGE_URL = "imageUrl"
    private const val DATA_BUTTONS = "buttons"
    private const val DATA_PUSH_CLICK_URL = "clickUrl"
    private const val DATA_PAYLOAD = "payload"
    override val tag: String = "FCM"

    private val gson by lazy { Gson() }
    override fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler = FirebaseServiceHandler(logger, exceptionHandler)

    override fun toString(): String = tag

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
        val data = remoteMessage?.data ?: return null
        val uniqueKey = data[DATA_UNIQUE_KEY] ?: return null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        return MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = data[DATA_TITLE] ?: "",
            description = data[DATA_MESSAGE] ?: "",
            pushActions = runCatching {
                gson.fromJson<List<PushAction>?>(
                    data[DATA_BUTTONS],
                    pushActionsType
                )
            }.getOrDefault(
                emptyList()
            ),
            pushLink = data[DATA_PUSH_CLICK_URL],
            imageUrl = data[DATA_IMAGE_URL],
            payload = data[DATA_PAYLOAD],
        )
    }
}