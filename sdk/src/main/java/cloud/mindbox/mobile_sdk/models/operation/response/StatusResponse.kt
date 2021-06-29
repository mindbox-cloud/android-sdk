package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class StatusResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("dateTimeUtc") val dateTimeUtc: DateTime? = null
) {
    override fun toString(): String {
        return "StatusResponse(ids=$ids, dateTimeUtc=$dateTimeUtc)"
    }
}