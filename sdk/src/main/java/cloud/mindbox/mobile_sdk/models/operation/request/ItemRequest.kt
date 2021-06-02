package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class ItemRequest(
    @SerializedName("basePricePerItem") val basePricePerItem: Double? = null,
    @SerializedName("product") val product: ProductRequest? = null,
    @SerializedName("minPricePerItem") val minPricePerItem: Double? = null,
    @SerializedName("requestedPromotions") val requestedPromotions: List<RequestedPromotionRequest>? = null
)
