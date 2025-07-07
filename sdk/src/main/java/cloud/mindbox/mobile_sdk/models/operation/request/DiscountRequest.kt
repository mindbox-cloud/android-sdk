package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class DiscountRequest private constructor(
    @SerializedName("type") public val type: DiscountTypeRequest? = null,
    @SerializedName("promoCode") public val promoCode: PromoCodeRequest? = null,
    @SerializedName("externalPromoAction") public val externalPromoAction: ExternalPromoActionRequest? = null,
    @SerializedName("amount") public val amount: Double? = null
) {

    public constructor(
        promoCode: PromoCodeRequest? = null,
        amount: Double? = null
    ) : this(
        type = DiscountTypeRequest.PROMO_CODE,
        promoCode = promoCode,
        amount = amount
    )

    public constructor(
        externalPromoAction: ExternalPromoActionRequest? = null,
        amount: Double? = null
    ) : this(
        type = DiscountTypeRequest.EXTERNAL_PROMO_ACTION,
        externalPromoAction = externalPromoAction,
        amount = amount
    )
}
