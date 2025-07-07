package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class UsageServiceStatusResponse {

    @SerializedName("available")
    AVAILABLE,

    @SerializedName("unavailable")
    UNAVAILABLE
}
