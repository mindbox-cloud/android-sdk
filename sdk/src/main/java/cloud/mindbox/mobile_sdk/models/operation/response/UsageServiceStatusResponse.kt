package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class UsageServiceStatusResponse {

    @SerializedName("available")
    AVAILABLE,

    @SerializedName("unavailable")
    UNAVAILABLE
}
