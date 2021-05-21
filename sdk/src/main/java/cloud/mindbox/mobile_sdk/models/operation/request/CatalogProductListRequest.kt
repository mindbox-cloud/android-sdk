package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeRequestAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class CatalogProductListRequest(
    @JsonAdapter(DateTimeRequestAdapter::class)
    @SerializedName("calculationDateTimeUtc") val calculationDateTimeUtc: DateTimeRequest? = null,
    @SerializedName("area") val area: AreaRequest? = null,
    @SerializedName("items") val items: List<RequestedPromotionRequest>? = null
)