package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class DiscountAmountTypeResponse {

    @SerializedName("Percent")
    PERCENT,

    @SerializedName("Absolute")
    ABSOLUTE
}
