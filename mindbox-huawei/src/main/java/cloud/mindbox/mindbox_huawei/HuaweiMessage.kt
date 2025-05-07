package cloud.mindbox.mindbox_huawei

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class HuaweiMessage(
    @SerializedName(DATA_UNIQUE_KEY) val uniqueKey: String,
    @SerializedName(DATA_TITLE) val title: String?,
    @SerializedName(DATA_MESSAGE) val message: String,
    @SerializedName(DATA_BUTTONS)
    @JsonAdapter(HuaweiPushActionsDeserializer::class)
    val buttons: List<PushAction>?,
    @SerializedName(DATA_PUSH_CLICK_URL) val clickUrl: String?,
    @SerializedName(DATA_IMAGE_URL) val imageUrl: String?,
    @SerializedName(DATA_PAYLOAD) val payload: String,
) {
    companion object {
        const val DATA_UNIQUE_KEY = "uniqueKey"
        const val DATA_TITLE = "title"
        const val DATA_MESSAGE = "message"
        const val DATA_IMAGE_URL = "imageUrl"
        const val DATA_BUTTONS = "buttons"
        const val DATA_PUSH_CLICK_URL = "clickUrl"
        const val DATA_PAYLOAD = "payload"
    }
}

internal data class PushAction(
    @SerializedName("uniqueKey") val uniqueKey: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
)
