package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class CatalogProductListRequest(
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("calculationDateTimeUtc") val calculationDateTimeUtc: DateTime? = null,
    @SerializedName("area") val area: AreaRequest? = null,
    @SerializedName("items") val items: List<ItemRequest>? = null
)