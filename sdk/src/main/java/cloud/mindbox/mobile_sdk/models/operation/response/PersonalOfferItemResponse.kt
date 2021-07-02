package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class PersonalOfferItemResponse(
    @SerializedName("product") val product: ProductResponse? = null,
    @SerializedName("benefit") val benefit: BenefitResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("startDateTimeUtc") val startDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("endDateTimeUtc") val endDateTimeUtc: DateTime? = null
) {
    override fun toString() =
        "PersonalOfferItemResponse(product=$product, benefit=$benefit, " +
            "startDateTimeUtc=$startDateTimeUtc, endDateTimeUtc=$endDateTimeUtc)"
}