package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class RequestedPromotionRequest(
    @SerializedName("type") public val type: RequestedPromotionTypeRequest? = null,
    @SerializedName("promotion") public val promotion: PromotionRequest? = null,
    @SerializedName("amount") public val amount: Double? = null,
    @SerializedName("coupon") public val coupon: CouponRequest? = null
)
