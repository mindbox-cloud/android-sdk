package cloud.mindbox.mobile_sdk.pushes

import com.google.gson.annotations.SerializedName

data class PushAction(
    @SerializedName("uniqueKey") val uniqueKey: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
)