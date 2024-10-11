package cloud.mindbox.mindbox_huawei

import com.google.gson.annotations.SerializedName

internal data class HuaweiMessage(
    @SerializedName("uniqueKey") val uniqueKey: String,
    @SerializedName("title") val title: String?,
    @SerializedName("message") val message: String,
    @SerializedName("buttons") val buttons: List<PushAction>,
    @SerializedName("clickUrl") val clickUrl: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("payload") val payload: String,
)

internal data class PushAction(
    @SerializedName("uniqueKey") val uniqueKey: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
)
