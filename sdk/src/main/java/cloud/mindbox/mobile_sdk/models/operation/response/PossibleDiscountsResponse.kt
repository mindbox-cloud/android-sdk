package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class PossibleDiscountsResponse(
    @SerializedName("discountsCount") public val discountsCount: Int? = null,
    @SerializedName("discount") public val discount: DiscountResponse? = null,
    @SerializedName("products") public val products: List<ProductResponse>? = null
) {

    override fun toString(): String = "PossibleDiscountsResponse(discountsCount=$discountsCount, " +
        "discount=$discount, products=$products)"
}
