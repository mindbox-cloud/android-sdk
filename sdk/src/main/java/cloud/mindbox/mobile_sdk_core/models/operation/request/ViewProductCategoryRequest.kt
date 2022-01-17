package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

open class ViewProductCategoryRequest(
    @SerializedName("productCategory") val productCategory: ProductCategoryRequest? = null,
    @SerializedName("customerAction") val customerAction: CustomerActionRequest? = null
)
