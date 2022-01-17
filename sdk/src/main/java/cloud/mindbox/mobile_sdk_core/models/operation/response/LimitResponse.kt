package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.DateTime
import cloud.mindbox.mobile_sdk_core.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class LimitResponse(
    @SerializedName("type") val type: LimitTypeResponse? = null,
    @SerializedName("amount") val amount: AmountResponse? = null,
    @SerializedName("used") private val used: Any? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("untilDateTimeUtc") val untilDateTimeUtc: DateTime? = null,
    @SerializedName("period") val period: PeriodType? = null
) {

    /**
     * Retrieve <code>used</code> field as [UsedResponse]
     */
    fun usedResponse() = used as? UsedResponse

    /**
     * Retrieve <code>used</code> field as [Double]
     */
    fun usedAmount() = used as? Double

    override fun toString() = "LimitResponse(type=$type, amount=$amount, used=$used, " +
            "untilDateTimeUtc=$untilDateTimeUtc, period=$period)"

}
