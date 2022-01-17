package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

enum class DiscountTypeRequest {
    @SerializedName("promoCode") PROMO_CODE,
    @SerializedName("externalPromoAction") EXTERNAL_PROMO_ACTION
}
