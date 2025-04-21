package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class ItemResponse(
    @SerializedName("basePricePerItem") val basePricePerItem: Double? = null,
    @SerializedName("product") val product: ProductResponse? = null,
    @SerializedName("minPricePerItem") val minPricePerItem: Double? = null,
    @SerializedName("priceForCustomer") val priceForCustomer: Double? = null,
    @SerializedName("appliedPromotions") val appliedPromotions: List<AppliedPromotionResponse>? = null,
    @SerializedName("placeholders") val placeholders: List<PlaceholderResponse>? = null
) {

    override fun toString() = "ItemResponse(basePricePerItem=$basePricePerItem, " +
        "product=$product, minPricePerItem=$minPricePerItem, " +
        "priceForCustomer=$priceForCustomer, appliedPromotions=$appliedPromotions, " +
        "placeholders=$placeholders)"
}
