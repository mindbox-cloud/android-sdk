package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class OperationResponse(
    status: String? = null,
    @SerializedName("customer") val customer: CustomerResponse? = null,
    @SerializedName("productList") val productList: List<ProductListItemResponse>? = null,
    @SerializedName("recommendations") val recommendations: List<RecommendationResponse>? = null,
    @SerializedName("customerSegmentations") val customerSegmentations: List<CustomerSegmentationResponse>? = null,
    @SerializedName("promoCode") val promoCode: PromoCodeResponse? = null
) : OperationResponseBase(status){

    override fun toString(): String {
        return "OperationResponse(customer=$customer, productList=$productList, recommendations=$recommendations, customerSegmentations=$customerSegmentations, promoCode=$promoCode)"
    }
}
