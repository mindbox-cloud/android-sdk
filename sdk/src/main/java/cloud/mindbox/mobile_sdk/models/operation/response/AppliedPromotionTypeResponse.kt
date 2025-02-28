package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public enum class AppliedPromotionTypeResponse {

    @SerializedName("discount")
    DISCOUNT,

    @SerializedName("correctionDiscount")
    CORRECTION_DISCOUNT,

    @SerializedName("deliveryDiscount")
    DELIVERY_DISCOUNT,

    @SerializedName("earnedBonusPoints")
    EARNED_BONUS_POINTS,

    @SerializedName("spentBonusPoints")
    SPEND_BONUS_POINTS,

    @SerializedName("issuedCoupon")
    ISSUED_COUPON,

    @SerializedName("message")
    MESSAGE,

    @SerializedName("preconditionMarker")
    PRECONDITION_MARKER
}
