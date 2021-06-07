package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.request.CatalogProductListRequest
import cloud.mindbox.mobile_sdk.models.operation.request.ProductListItemRequest
import com.google.gson.annotations.SerializedName

open class OperationResponse(
    status: String? = null,
    @SerializedName("customer") val customer: CustomerResponse? = null,
    @SerializedName("productList") val productList: Any? = null,
    @SerializedName("recommendations") val recommendations: List<RecommendationResponse>? = null,
    @SerializedName("customerSegmentations") val customerSegmentations: List<CustomerSegmentationResponse>? = null,
    @SerializedName("promoCode") val promoCode: PromoCodeResponse? = null
) : OperationResponseBase(status) {

    /** Used for catalog with name productList and its type is [CatalogProductListRequest] **/
    fun productList(): CatalogProductListRequest? = productList as? CatalogProductListRequest

    /** Used for product with name productList and its is array of [ProductListItemRequest] **/
    fun productListItems() = (productList as? List<*>)?.mapNotNull { it as? ProductListItemRequest }

    override fun toString(): String {
        return "OperationResponse(customer=$customer, productList=$productList, recommendations=$recommendations, customerSegmentations=$customerSegmentations, promoCode=$promoCode)"
    }
}
