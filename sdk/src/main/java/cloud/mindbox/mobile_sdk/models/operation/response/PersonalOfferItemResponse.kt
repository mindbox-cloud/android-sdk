package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class PersonalOfferItemResponse(
    @SerializedName("product") public val product: ProductResponse? = null,
    @SerializedName("benefit") public val benefit: BenefitResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("startDateTimeUtc") public val startDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("endDateTimeUtc") public val endDateTimeUtc: DateTime? = null
) {

    override fun toString(): String =
        "PersonalOfferItemResponse(product=$product, benefit=$benefit, " +
            "startDateTimeUtc=$startDateTimeUtc, endDateTimeUtc=$endDateTimeUtc)"
}
