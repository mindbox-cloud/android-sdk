package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

enum class AmountTypeResponse {

    @SerializedName("quantity") QUANTITY,
    @SerializedName("discountAmount") DISCOUNT_AMOUNT,
    @SerializedName("Price") PRICE,
    @SerializedName("Percent") PERCENT,
    @SerializedName("Absolute") ABSOLUTE

}
