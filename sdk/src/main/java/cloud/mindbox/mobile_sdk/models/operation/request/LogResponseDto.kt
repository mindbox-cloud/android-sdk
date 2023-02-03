package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

internal data class LogResponseDto(
    @SerializedName("status")
    val status: String,
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("content")
    val content: MutableList<String>
)
