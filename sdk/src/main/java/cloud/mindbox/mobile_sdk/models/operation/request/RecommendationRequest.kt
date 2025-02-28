package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class RecommendationRequest(
    @SerializedName("limit") public val limit: Int? = null,
    @SerializedName("area") public val area: AreaRequest? = null,
    @SerializedName("productCategory") public val productCategory: ProductCategoryRequest? = null,
    @SerializedName("product") public val product: ProductRequest? = null
)
