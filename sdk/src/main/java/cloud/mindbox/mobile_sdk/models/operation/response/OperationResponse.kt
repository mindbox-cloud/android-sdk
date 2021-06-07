package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.adapters.ProductListResponseAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class OperationResponse(
    status: String? = null,
    @SerializedName("customer") val customer: CustomerResponse? = null,
    @JsonAdapter(ProductListResponseAdapter::class)
    @SerializedName("productList") private val productList: Any? = null,
    @SerializedName("recommendations") val recommendations: List<RecommendationResponse>? = null,
    @SerializedName("customerSegmentations") val customerSegmentations: List<CustomerSegmentationResponse>? = null,
    @SerializedName("promoCode") val promoCode: PromoCodeResponse? = null
) : OperationResponseBase(status) {

    /** Used for catalog with name productList and its type is [CatalogProductListResponse] **/
    fun catalogProductList(): CatalogProductListResponse? =
        productList as? CatalogProductListResponse

    /** Used for product with name productList and its is array of [ProductListItemResponse] **/
    fun productListItems() =
        (productList as? List<*>)?.mapNotNull { it as? ProductListItemResponse }

    override fun toString(): String {
        return "OperationResponse(customer=$customer, productList=$productList, " +
                "recommendations=$recommendations, customerSegmentations=$customerSegmentations, " +
                "promoCode=$promoCode)"
    }

}
