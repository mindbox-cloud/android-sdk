package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import com.google.gson.annotations.SerializedName

open class NearestExpirationResponse(
    @SerializedName("total") val total: Double? = null,
    @SerializedName("date") val date: DateOnly? = null
) {
    override fun toString(): String {
        return "NearestExpirationResponse(total=$total, date=$date)"
    }
}