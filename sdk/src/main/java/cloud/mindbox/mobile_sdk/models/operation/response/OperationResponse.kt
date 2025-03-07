package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.adapters.ProductListResponseAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class OperationResponse(
    status: String? = null,
    @SerializedName("customer") public val customer: CustomerResponse? = null,
    @JsonAdapter(ProductListResponseAdapter::class)
    @SerializedName("productList") private val productList: Any? = null,
    @SerializedName("recommendations") public val recommendations: List<RecommendationResponse>? = null,
    @SerializedName("customerSegmentations") public val customerSegmentations: List<CustomerSegmentationResponse>? = null,
    @SerializedName("promoCode") public val promoCode: PromoCodeResponse? = null,
    @SerializedName("personalOffers") public val personalOffers: List<PersonalOfferItemResponse>? = null,
    @SerializedName("balances") public val balances: List<BalanceResponse>? = null,
    @SerializedName("discountCards") public val discountCards: List<DiscountCardResponse>? = null,
    @SerializedName("promoActions") public val promoActions: List<PromoActionResponse>? = null,
    @SerializedName("retailOrderStatistics") public val retailOrderStatistics: RetailOrderStatisticsResponse? = null
) : OperationResponseBase(status) {

    /** Used for catalog with name productList and its type is [CatalogProductListResponse] **/
    public fun catalogProductList(): CatalogProductListResponse? =
        productList as? CatalogProductListResponse

    /** Used for product with name productList and its is array of [ProductListItemResponse] **/
    public fun productListItems(): List<ProductListItemResponse>? =
        (productList as? List<*>)?.mapNotNull { it as? ProductListItemResponse }

    override fun toString(): String =
        "OperationResponse(status=$status, customer=$customer, productList=$productList, " +
            "recommendations=$recommendations, customerSegmentations=$customerSegmentations, " +
            "promoCode=$promoCode, personalOffers=$personalOffers, balances=$balances, " +
            "discountCards=$discountCards, promoActions=$promoActions, " +
            "retailOrderStatistics=$retailOrderStatistics)"
}
