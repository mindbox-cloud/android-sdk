package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.google.firebase.messaging.RemoteMessage as FirebaseRemoteMessage

internal class FirebaseRemoteMessageTransformer(private val exceptionHandler: ExceptionHandler) {

    companion object {

        private const val DATA_UNIQUE_KEY = "uniqueKey"
        private const val DATA_TITLE = "title"
        private const val DATA_MESSAGE = "message"
        private const val DATA_IMAGE_URL = "imageUrl"
        private const val DATA_BUTTONS = "buttons"
        private const val DATA_PUSH_CLICK_URL = "clickUrl"

    }

    private val gson by lazy { Gson() }

    fun transform(
        remoteMessage: FirebaseRemoteMessage?,
    ): RemoteMessage? = exceptionHandler.runCatching(defaultValue = null) {
        val data = remoteMessage?.data ?: return@runCatching null
        val uniqueKey = data[DATA_UNIQUE_KEY] ?: return@runCatching null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        val pushActions = getButtons(data, pushActionsType)
        RemoteMessage(
            uniqueKey = uniqueKey,
            title = data[DATA_TITLE] ?: "",
            description = data[DATA_MESSAGE] ?: "",
            pushActions = pushActions,
            pushLink = data[DATA_PUSH_CLICK_URL],
            imageUrl = data[DATA_IMAGE_URL],
        )
    }

    private fun getButtons(
        data: Map<String, String>,
        pushActionsType: Type?,
    ) = exceptionHandler.runCatching(defaultValue = listOf()) {
        gson.fromJson<List<PushAction>>(data[DATA_BUTTONS], pushActionsType)
    }

}