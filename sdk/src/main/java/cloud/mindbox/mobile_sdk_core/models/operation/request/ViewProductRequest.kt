package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

open class ViewProductRequest private constructor(
    @SerializedName("product") val product: ProductRequest? = null,
    @SerializedName("productGroup") val productGroup: ProductGroupRequest? = null,
    @SerializedName("customerAction") val customerAction: CustomerActionRequest? = null
) {

    constructor(customerAction: CustomerActionRequest? = null) : this(null, null, customerAction)

    constructor(
        product: ProductRequest,
        customerAction: CustomerActionRequest? = null
    ) : this(product, null, customerAction)

    constructor(
        productGroup: ProductGroupRequest,
        customerAction: CustomerActionRequest? = null
    ) : this(null, productGroup, customerAction)

}
