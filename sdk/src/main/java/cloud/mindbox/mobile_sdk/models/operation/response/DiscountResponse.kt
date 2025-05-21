package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class DiscountResponse(
    @SerializedName("amount") public val amount: Double? = null,
    @SerializedName("amountType") public val amountType: DiscountAmountTypeResponse? = null
) {

    override fun toString(): String = "DiscountResponse(amount=$amount, amountType=$amountType)"
}
