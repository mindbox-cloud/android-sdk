package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class PromoCodeRequest(
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableFromDateTimeUtc") val availableFromDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableTillDateTimeUtc") val availableTillDateTimeUtc: DateTime? = null,
    @SerializedName("ids") val ids: Ids? = null
)
