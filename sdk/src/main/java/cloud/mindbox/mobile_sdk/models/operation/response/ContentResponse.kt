package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class ContentResponse(
    @SerializedName("type") val type: ContentTypeResponse? = null,
    @SerializedName("promotion") val promotion: PromotionResponse? = null,
    @SerializedName("possibleDiscounts") val possibleDiscounts: PossibleDiscountsResponse? = null,
    @SerializedName("message") val message: String? = null
) {

    override fun toString() = "ContentResponse(type=$type, promotion=$promotion, " +
        "possibleDiscounts=$possibleDiscounts, message=$message)"
}
