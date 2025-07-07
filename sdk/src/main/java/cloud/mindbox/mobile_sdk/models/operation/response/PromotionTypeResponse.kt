package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class PromotionTypeResponse {

    @SerializedName("mindbox")
    MINDBOX,

    @SerializedName("external")
    EXTERNAL
}
