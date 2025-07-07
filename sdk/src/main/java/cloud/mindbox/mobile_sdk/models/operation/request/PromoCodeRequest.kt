package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class PromoCodeRequest(
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableFromDateTimeUtc") public val availableFromDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableTillDateTimeUtc") public val availableTillDateTimeUtc: DateTime? = null,
    @SerializedName("ids") public val ids: Ids? = null
)
