package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

open class RecommendationRequest(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("area") val area: AreaRequest? = null,
    @SerializedName("productCategory") val productCategory: ProductCategoryRequest? = null,
    @SerializedName("product") val product: ProductRequest? = null
)