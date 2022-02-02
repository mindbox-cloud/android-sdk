package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class ViewProductCategoryRequest(
    @SerializedName("productCategory") val productCategory: ProductCategoryRequest? = null,
    @SerializedName("customerAction") val customerAction: CustomerActionRequest? = null
)
