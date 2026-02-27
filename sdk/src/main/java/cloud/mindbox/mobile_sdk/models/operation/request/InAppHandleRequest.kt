package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

internal data class InAppHandleRequest(
    @SerializedName("inappId")
    val inAppId: String
)

internal data class InAppShowRequest(
    @SerializedName("inappId")
    val inAppId: String,
    @SerializedName("timeToDisplay")
    val timeToDisplay: String,
    @SerializedName("tags")
    val tags: Map<String, String>?
)
