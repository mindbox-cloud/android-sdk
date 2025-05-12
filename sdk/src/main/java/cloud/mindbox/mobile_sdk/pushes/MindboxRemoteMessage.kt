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
) {
    public companion object {
        public const val DATA_UNIQUE_KEY: String = "uniqueKey"
        public const val DATA_TITLE: String = "title"
        public const val DATA_MESSAGE: String = "message"
        public const val DATA_IMAGE_URL: String = "imageUrl"
        public const val DATA_BUTTONS: String = "buttons"
        public const val DATA_PUSH_CLICK_URL: String = "clickUrl"
        public const val DATA_PAYLOAD: String = "payload"
    }
}
