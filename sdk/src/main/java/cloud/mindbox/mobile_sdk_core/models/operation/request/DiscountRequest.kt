package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

open class DiscountRequest private constructor(
    @SerializedName("type") val type: DiscountTypeRequest? = null,
    @SerializedName("promoCode") val promoCode: PromoCodeRequest? = null,
    @SerializedName("externalPromoAction") val externalPromoAction: ExternalPromoActionRequest? = null,
    @SerializedName("amount") val amount: Double? = null
) {

    constructor(
        promoCode: PromoCodeRequest? = null,
        amount: Double? = null
    ) : this(
        type = DiscountTypeRequest.PROMO_CODE,
        promoCode = promoCode,
        amount = amount
    )

    constructor(
        externalPromoAction: ExternalPromoActionRequest? = null,
        amount: Double? = null
    ) : this(
        type = DiscountTypeRequest.EXTERNAL_PROMO_ACTION,
        externalPromoAction = externalPromoAction,
        amount = amount
    )

}
