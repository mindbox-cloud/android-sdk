package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class BenefitResponse(
    @SerializedName("amount") val amount: AmountResponse? = null,
    @SerializedName("limit") val limit: LimitResponse? = null
) {

    override fun toString() = "BenefitResponse(amount=$amount, limit=$limit)"

}