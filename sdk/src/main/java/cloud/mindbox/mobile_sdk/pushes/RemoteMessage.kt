package cloud.mindbox.mobile_sdk.pushes

data class RemoteMessage(
    val uniqueKey: String,
    val title: String,
    val description: String,
    val pushActions: List<PushAction>,
    val pushLink: String?,
    val imageUrl: String?,
    val payload: String?,
)
