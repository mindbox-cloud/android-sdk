package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateOnlyAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class NearestExpirationResponse(
    @SerializedName("total") public val total: Double? = null,
    @JsonAdapter(DateOnlyAdapter::class)
    @SerializedName("date") public val date: DateOnly? = null
) {

    override fun toString(): String = "NearestExpirationResponse(total=$total, date=$date)"
}
