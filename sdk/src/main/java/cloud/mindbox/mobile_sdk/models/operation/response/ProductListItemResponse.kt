package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class ProductListItemResponse constructor(
    @SerializedName("count") public val count: Double? = null,
    @SerializedName("product") public val product: ProductResponse? = null,
    @SerializedName("productGroup") public val productGroup: ProductGroupResponse? = null,
    @SerializedName("pricePerItem") public val pricePerItem: Double? = null,
    @SerializedName("priceOfLine") public val priceOfLine: Double? = null,
    @SerializedName("price") public val price: Double? = null
) {

    override fun toString(): String = "ProductListItemResponse(count=$count, product=$product, " +
        "productGroup=$productGroup, pricePerItem=$pricePerItem, priceOfLine=$priceOfLine, " +
        "price=$price)"
}
