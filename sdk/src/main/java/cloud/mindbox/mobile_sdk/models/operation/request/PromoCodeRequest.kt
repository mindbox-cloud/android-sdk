package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeRequestAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class PromoCodeRequest(
    @JsonAdapter(DateTimeRequestAdapter::class)
    @SerializedName("availableFromDateTimeUtc") val availableFromDateTimeUtc: DateTimeRequest? = null,
    @JsonAdapter(DateTimeRequestAdapter::class)
    @SerializedName("availableTillDateTimeUtc") val availableTillDateTimeUtc: DateTimeRequest? = null,
    @SerializedName("ids") val ids: Ids? = null
)
