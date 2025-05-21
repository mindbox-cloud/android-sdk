package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class DiscountAmountTypeResponse {

    @SerializedName("Percent")
    PERCENT,

    @SerializedName("Absolute")
    ABSOLUTE
}
