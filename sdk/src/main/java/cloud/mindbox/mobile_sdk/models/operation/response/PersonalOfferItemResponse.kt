package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import com.google.gson.annotations.SerializedName

open class PersonalOfferItemResponse(
    @SerializedName("product") val product: ProductResponse? = null,
    @SerializedName("benefit") val benefit: BenefitResponse? = null,
    @SerializedName("startDateTimeUtc") val startDateTimeUtc: DateTime? = null,
    @SerializedName("endDateTimeUtc") val endDateTimeUtc: DateTime? = null
) {
    override fun toString(): String {
        return "PersonalOfferItemResponse(product=$product, benefit=$benefit, " +
                "startDateTimeUtc=$startDateTimeUtc, endDateTimeUtc=$endDateTimeUtc)"
    }
}