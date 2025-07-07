package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class ItemRequest(
    @SerializedName("basePricePerItem") public val basePricePerItem: Double? = null,
    @SerializedName("product") public val product: ProductRequest? = null,
    @SerializedName("minPricePerItem") public val minPricePerItem: Double? = null,
    @SerializedName("requestedPromotions") public val requestedPromotions: List<RequestedPromotionRequest>? = null
)
