package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class ProductListItemRequest private constructor(
    @SerializedName("count") public val count: Double? = null,
    @SerializedName("product") public val product: ProductRequest? = null,
    @SerializedName("productGroup") public val productGroup: ProductGroupRequest? = null,
    @SerializedName("pricePerItem") public val pricePerItem: Double? = null,
    @SerializedName("priceOfLine") public val priceOfLine: Double? = null
) {

    public constructor(count: Double? = null) : this(
        count = count,
        product = null,
        productGroup = null,
        pricePerItem = null,
        priceOfLine = null
    )

    public constructor(
        count: Double,
        price: Double,
        isPricePerItem: Boolean
    ) : this(
        count = count,
        pricePerItem = if (isPricePerItem) price else null,
        priceOfLine = if (!isPricePerItem) price else null
    )

    public constructor(
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

    public constructor(
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

    public constructor(
        product: ProductRequest,
        pricePerItem: Double? = null
    ) : this(
        count = null,
        product = product,
        pricePerItem = pricePerItem
    )

    public constructor(
        productGroup: ProductGroupRequest,
        pricePerItem: Double? = null
    ) : this(
        count = null,
        productGroup = productGroup,
        pricePerItem = pricePerItem
    )
}
