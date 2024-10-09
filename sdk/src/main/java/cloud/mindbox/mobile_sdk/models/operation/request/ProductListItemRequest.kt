package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class ProductListItemRequest private constructor(
    @SerializedName("count") val count: Double? = null,
    @SerializedName("product") val product: ProductRequest? = null,
    @SerializedName("productGroup") val productGroup: ProductGroupRequest? = null,
    @SerializedName("pricePerItem") val pricePerItem: Double? = null,
    @SerializedName("priceOfLine") val priceOfLine: Double? = null
) {

    constructor(count: Double? = null) : this(
        count = count,
        product = null,
        productGroup = null,
        pricePerItem = null,
        priceOfLine = null
    )

    constructor(
        count: Double,
        price: Double,
        isPricePerItem: Boolean
    ) : this(
        count = count,
        pricePerItem = if (isPricePerItem) price else null,
        priceOfLine = if (!isPricePerItem) price else null
    )

    constructor(
        count: Double,
        product: ProductRequest,
        price: Double? = null,
        isPricePerItem: Boolean? = null
    ) : this(
        count = count,
        product = product,
        pricePerItem = if (isPricePerItem == true) price else null,
        priceOfLine = if (isPricePerItem == false) price else null
    )

    constructor(
        count: Double,
        productGroup: ProductGroupRequest,
        price: Double? = null,
        isPricePerItem: Boolean? = null
    ) : this(
        count = count,
        productGroup = productGroup,
        pricePerItem = if (isPricePerItem == true) price else null,
        priceOfLine = if (isPricePerItem == false) price else null
    )

    constructor(
        product: ProductRequest,
        pricePerItem: Double? = null
    ) : this(
        count = null,
        product = product,
        pricePerItem = pricePerItem
    )

    constructor(
        productGroup: ProductGroupRequest,
        pricePerItem: Double? = null
    ) : this(
        count = null,
        productGroup = productGroup,
        pricePerItem = pricePerItem
    )
}
