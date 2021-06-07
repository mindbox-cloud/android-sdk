package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class LimitResponse(
    @SerializedName("type") val type: LimitTypeResponse? = null,
    @SerializedName("amount") val amount: AmountResponse? = null,
    @SerializedName("used") val used: UsedResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("untilDateTimeUtc") val untilDateTimeUtc: DateTime? = null
) {

    override fun toString() = "LimitResponse(type=$type, amount=$amount, used=$used, " +
            "untilDateTimeUtc=$untilDateTimeUtc)"

}
