package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

internal data class InAppHandleRequest(
    @SerializedName("inappId")
    val inAppId: String
)
