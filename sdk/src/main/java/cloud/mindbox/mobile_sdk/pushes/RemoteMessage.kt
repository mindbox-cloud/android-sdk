package cloud.mindbox.mobile_sdk.pushes
/**
 * A class for internal sdk work only. Do not extend or use it
 * */
data class RemoteMessage(
    val uniqueKey: String,
    val title: String,
    val description: String,
    val pushActions: List<PushAction>,
    val pushLink: String?,
    val imageUrl: String?,
    val payload: String?,
)
