package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class ItemResponse(
    @SerializedName("basePricePerItem") public val basePricePerItem: Double? = null,
    @SerializedName("product") public val product: ProductResponse? = null,
    @SerializedName("minPricePerItem") public val minPricePerItem: Double? = null,
    @SerializedName("priceForCustomer") public val priceForCustomer: Double? = null,
    @SerializedName("appliedPromotions") public val appliedPromotions: List<AppliedPromotionResponse>? = null,
    @SerializedName("placeholders") public val placeholders: List<PlaceholderResponse>? = null
) {

    override fun toString(): String = "ItemResponse(basePricePerItem=$basePricePerItem, " +
        "product=$product, minPricePerItem=$minPricePerItem, " +
        "priceForCustomer=$priceForCustomer, appliedPromotions=$appliedPromotions, " +
        "placeholders=$placeholders)"
}
