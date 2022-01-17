package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

enum class UsageServiceStatusResponse {

    @SerializedName("available") AVAILABLE,
    @SerializedName("unavailable") UNAVAILABLE

}
