package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.DateTime
import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import cloud.mindbox.mobile_sdk_core.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class StatusResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("dateTimeUtc") val dateTimeUtc: DateTime? = null
) {

    override fun toString() = "StatusResponse(ids=$ids, dateTimeUtc=$dateTimeUtc)"

}