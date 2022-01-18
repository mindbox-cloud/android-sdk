package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class RequestedPromotionRequest(
    @SerializedName("type") val type: RequestedPromotionTypeRequest? = null,
    @SerializedName("promotion") val promotion: PromotionRequest? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("coupon") val coupon: CouponRequest? = null
)
