package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class ViewProductCategoryRequest(
    @SerializedName("productCategory") public val productCategory: ProductCategoryRequest? = null,
    @SerializedName("customerAction") public val customerAction: CustomerActionRequest? = null
)
