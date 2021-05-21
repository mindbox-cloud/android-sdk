package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class DiscountRequest(
    @SerializedName("type") val type: DiscountTypeRequest? = null,
    @SerializedName("promoCode") val promoCode: PromoCodeRequest? = null,
    @SerializedName("externalPromoAction") val externalPromoAction: ExternalPromoActionRequest? = null,
    @SerializedName("amount") val amount: Double? = null
)
