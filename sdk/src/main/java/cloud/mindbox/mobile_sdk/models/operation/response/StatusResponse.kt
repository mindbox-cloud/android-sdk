package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class StatusResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("dateTimeUtc") public val dateTimeUtc: DateTime? = null
) {

    override fun toString(): String = "StatusResponse(ids=$ids, dateTimeUtc=$dateTimeUtc)"
}
