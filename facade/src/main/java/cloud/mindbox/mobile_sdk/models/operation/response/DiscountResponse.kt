package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class DiscountResponse(
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("amountType") val amountType: DiscountAmountTypeResponse? = null
) {

    override fun toString() = "DiscountResponse(amount=$amount, amountType=$amountType)"

}
