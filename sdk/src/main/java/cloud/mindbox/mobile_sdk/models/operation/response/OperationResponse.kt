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
    @SerializedName("promoCode") val promoCode: PromoCodeResponse? = null,
    @SerializedName("personalOffers") val personalOffers: List<PersonalOfferItemResponse>? = null,
    @SerializedName("balances") val balances: List<BalanceResponse>? = null,
    @SerializedName("discountCards") val discountCards: List<DiscountCardResponse>? = null,
    @SerializedName("promoActions") val promoActions: List<PromoActionResponse>? = null,
    @SerializedName("retailOrderStatistics") val retailOrderStatistics: RetailOrderStatisticsResponse? = null
) : OperationResponseBase(status) {

    /** Used for catalog with name productList and its type is [CatalogProductListResponse] **/
    fun catalogProductList(): CatalogProductListResponse? =
        productList as? CatalogProductListResponse

    /** Used for product with name productList and its is array of [ProductListItemResponse] **/
    fun productListItems() =
        (productList as? List<*>)?.mapNotNull { it as? ProductListItemResponse }

    override fun toString() =
        "OperationResponse(status=$status, customer=$customer, productList=$productList, " +
                "recommendations=$recommendations, customerSegmentations=$customerSegmentations, " +
                "promoCode=$promoCode, personalOffers=$personalOffers, balances=$balances, " +
                "discountCards=$discountCards, promoActions=$promoActions, " +
                "retailOrderStatistics=$retailOrderStatistics)"

}
