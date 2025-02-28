package cloud.mindbox.mobile_sdk.pushes

/**
 * A class representing mindbox remote message
 * You can use it as a model to store data from mindbox
 * with your custom push notification implementation.
 * */
public data class MindboxRemoteMessage(
    val uniqueKey: String,
    val title: String,
    val description: String,
    val pushActions: List<PushAction>,
    val pushLink: String?,
    val imageUrl: String?,
    val payload: String?,
)
