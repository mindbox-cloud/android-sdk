package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

open class PossibleDiscountsResponse(
    @SerializedName("discountsCount") val discountsCount: Int? = null,
    @SerializedName("discount") val discount: DiscountResponse? = null,
    @SerializedName("products") val products: List<ProductResponse>? = null
) {

    override fun toString() = "PossibleDiscountsResponse(discountsCount=$discountsCount, " +
            "discount=$discount, products=$products)"

}
