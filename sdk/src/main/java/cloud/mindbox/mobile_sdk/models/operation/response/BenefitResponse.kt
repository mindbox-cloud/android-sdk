package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class BenefitResponse(
    @SerializedName("amount") public val amount: AmountResponse? = null,
    @SerializedName("limit") public val limit: LimitResponse? = null
) {

    override fun toString(): String = "BenefitResponse(amount=$amount, limit=$limit)"
}
