package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

enum class RequestedPromotionTypeRequest {

    @SerializedName("balance") BALANCE,
    @SerializedName("externalPromoAction") EXTERNAL_PROMO_ACTION,
    @SerializedName("issuedCoupon") ISSUED_COUPON,
    @SerializedName("message") MESSAGE,
    @SerializedName("promoCode") PROMO_CODE,
    @SerializedName("discount") DISCOUNT,
    @SerializedName("spentBonusPoints") SPENT_BONUS_POINTS,
    @SerializedName("earnedBonusPoints") EARNED_BONUS_POINTS

}
