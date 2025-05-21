package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class ContentResponse(
    @SerializedName("type") public val type: ContentTypeResponse? = null,
    @SerializedName("promotion") public val promotion: PromotionResponse? = null,
    @SerializedName("possibleDiscounts") public val possibleDiscounts: PossibleDiscountsResponse? = null,
    @SerializedName("message") public val message: String? = null
) {

    override fun toString(): String = "ContentResponse(type=$type, promotion=$promotion, " +
        "possibleDiscounts=$possibleDiscounts, message=$message)"
}
