package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public enum class DiscountTypeRequest {
    @SerializedName("promoCode")
    PROMO_CODE,

    @SerializedName("externalPromoAction")
    EXTERNAL_PROMO_ACTION
}
