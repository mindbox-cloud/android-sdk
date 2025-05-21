package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import com.google.gson.annotations.SerializedName

public open class LineRequest private constructor(
    @SerializedName("basePricePerItem") public val basePricePerItem: Double? = null,
    @SerializedName("quantity") public val quantity: Number? = null,
    @SerializedName("quantityType") public val quantityType: QuantityTypeRequest? = null,
    @SerializedName("minPricePerItem") public val minPricePerItem: Double? = null,
    @SerializedName("costPricePerItem") public val costPricePerItem: Double? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("discountedPricePerLine") public val discountedPricePerLine: Double? = null,
    @SerializedName("lineId") public val lineId: String? = null,
    @SerializedName("lineNumber") public val lineNumber: Int? = null,
    @SerializedName("discounts") public val discounts: List<DiscountRequest>? = null,
    @SerializedName("product") public val product: ProductRequest? = null
) {

    public constructor(
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

    public constructor(
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
