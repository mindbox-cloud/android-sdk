package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.pushes.PushAction
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.messaging.RemoteMessage as FirebaseRemoteMessage

internal object FirebaseRemoteMessageTransformer {

    private const val DATA_UNIQUE_KEY = "uniqueKey"
    private const val DATA_TITLE = "title"
    private const val DATA_MESSAGE = "message"
    private const val DATA_IMAGE_URL = "imageUrl"
    private const val DATA_BUTTONS = "buttons"
    private const val DATA_PUSH_CLICK_URL = "clickUrl"

    private val gson by lazy { Gson() }

    fun transform(remoteMessage: FirebaseRemoteMessage?): RemoteMessage? {
        val data = remoteMessage?.data ?: return null
        val uniqueKey = data[DATA_UNIQUE_KEY] ?: return null
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        val pushActions = gson.fromJson<List<PushAction>>(data[DATA_BUTTONS], pushActionsType)
        return RemoteMessage(
            uniqueKey = uniqueKey,
            title = data[DATA_TITLE] ?: "",
            description = data[DATA_MESSAGE] ?: "",
            pushActions = pushActions,
            pushLink = data[DATA_PUSH_CLICK_URL],
            imageUrl = data[DATA_IMAGE_URL],
        )
    }

}