package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class AppliedPromotionResponse(
    @SerializedName("type") public val type: AppliedPromotionTypeResponse? = null,
    @SerializedName("coupon") public val coupon: CouponResponse? = null,
    @SerializedName("promotion") public val promotion: PromotionResponse? = null,
    @SerializedName("limits") public val limits: List<LimitResponse>? = null,
    @SerializedName("spentBonusPointsAmount") public val spentBonusPointsAmount: Double? = null,
    @SerializedName("amount") public val amount: Double? = null,
    @SerializedName("groupingKey") public val groupingKey: String? = null,
    @SerializedName("balanceType") public val balanceType: BalanceTypeResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("expirationDateTimeUtc") public val expirationDateTimeUtc: DateTime? = null,
    @SerializedName("issuedCoupon") public val issuedCoupon: CouponResponse? = null
) {

    override fun toString(): String = "AppliedPromotionResponse(type=$type, coupon=$coupon, " +
        "promotion=$promotion, limits=$limits, spentBonusPointsAmount=$spentBonusPointsAmount, " +
        "amount=$amount, groupingKey=$groupingKey, balanceType=$balanceType, " +
        "expirationDateTimeUtc=$expirationDateTimeUtc, issuedCoupon=$issuedCoupon)"
}
