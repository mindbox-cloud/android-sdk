package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class AppliedPromotionResponse(
    @SerializedName("type") val type: AppliedPromotionTypeResponse? = null,
    @SerializedName("coupon") val coupon: CouponResponse? = null,
    @SerializedName("promotion") val promotion: PromotionResponse? = null,
    @SerializedName("limits") val limits: List<LimitResponse>? = null,
    @SerializedName("spentBonusPointsAmount") val spentBonusPointsAmount: Double? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("groupingKey") val groupingKey: String? = null,
    @SerializedName("balanceType") val balanceType: BalanceTypeResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("expirationDateTimeUtc") val expirationDateTimeUtc: DateTime? = null,
    @SerializedName("issuedCoupon") val issuedCoupon: CouponResponse? = null
) {

    override fun toString() = "AppliedPromotionResponse(type=$type, coupon=$coupon, " +
        "promotion=$promotion, limits=$limits, spentBonusPointsAmount=$spentBonusPointsAmount, " +
        "amount=$amount, groupingKey=$groupingKey, balanceType=$balanceType, " +
        "expirationDateTimeUtc=$expirationDateTimeUtc, issuedCoupon=$issuedCoupon)"
}
