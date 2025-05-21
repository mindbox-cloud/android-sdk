package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class ViewProductRequest private constructor(
    @SerializedName("product") public val product: ProductRequest? = null,
    @SerializedName("productGroup") public val productGroup: ProductGroupRequest? = null,
    @SerializedName("customerAction") public val customerAction: CustomerActionRequest? = null
) {

    public constructor(customerAction: CustomerActionRequest? = null) : this(null, null, customerAction)

    public constructor(
        product: ProductRequest,
        customerAction: CustomerActionRequest? = null
    ) : this(product, null, customerAction)

    public constructor(
        productGroup: ProductGroupRequest,
        customerAction: CustomerActionRequest? = null
    ) : this(null, productGroup, customerAction)
}
