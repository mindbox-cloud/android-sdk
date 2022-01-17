package cloud.mindbox.mobile_sdk_core.models.operation.request

import cloud.mindbox.mobile_sdk_core.models.operation.CustomFields
import com.google.gson.annotations.SerializedName

open class LineRequest private constructor(
    @SerializedName("basePricePerItem") val basePricePerItem: Double? = null,
    @SerializedName("quantity") val quantity: Number? = null,
    @SerializedName("quantityType") val quantityType: QuantityTypeRequest? = null,
    @SerializedName("minPricePerItem") val minPricePerItem: Double? = null,
    @SerializedName("costPricePerItem") val costPricePerItem: Double? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("discountedPricePerLine") val discountedPricePerLine: Double? = null,
    @SerializedName("lineId") val lineId: String? = null,
    @SerializedName("lineNumber") val lineNumber: Int? = null,
    @SerializedName("discounts") val discounts: List<DiscountRequest>? = null,
    @SerializedName("product") val product: ProductRequest? = null
) {

    constructor(
        basePricePerItem: Double,
        quantity: Double,
        minPricePerItem: Double? = null,
        costPricePerItem: Double? = null,
        customFields: CustomFields? = null,
        discountedPricePerLine: Double? = null,
        lineId: String? = null,
        lineNumber: Int? = null,
        discounts: List<DiscountRequest>? = null,
        product: ProductRequest? = null
    ) : this(
        basePricePerItem = basePricePerItem,
        quantity = quantity,
        quantityType = QuantityTypeRequest.DOUBLE,
        minPricePerItem = minPricePerItem,
        costPricePerItem = costPricePerItem,
        customFields = customFields,
        discountedPricePerLine = discountedPricePerLine,
        lineId = lineId,
        lineNumber = lineNumber,
        discounts = discounts,
        product = product
    )

    constructor(
        basePricePerItem: Double,
        quantity: Int,
        minPricePerItem: Double? = null,
        costPricePerItem: Double? = null,
        customFields: CustomFields? = null,
        discountedPricePerLine: Double? = null,
        lineId: String? = null,
        lineNumber: Int? = null,
        discounts: List<DiscountRequest>? = null,
        product: ProductRequest? = null
    ) : this(
        basePricePerItem = basePricePerItem,
        quantity = quantity,
        quantityType = QuantityTypeRequest.INT,
        minPricePerItem = minPricePerItem,
        costPricePerItem = costPricePerItem,
        customFields = customFields,
        discountedPricePerLine = discountedPricePerLine,
        lineId = lineId,
        lineNumber = lineNumber,
        discounts = discounts,
        product = product
    )

}
