package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class CatalogProductListRequest(
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("calculationDateTimeUtc") public val calculationDateTimeUtc: DateTime? = null,
    @SerializedName("area") public val area: AreaRequest? = null,
    @SerializedName("items") public val items: List<ItemRequest>? = null
)
