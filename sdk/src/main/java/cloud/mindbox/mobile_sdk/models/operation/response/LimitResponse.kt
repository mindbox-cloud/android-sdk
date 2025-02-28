package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class LimitResponse(
    @SerializedName("type") public val type: LimitTypeResponse? = null,
    @SerializedName("amount") public val amount: AmountResponse? = null,
    @SerializedName("used") private val used: Any? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("untilDateTimeUtc") public val untilDateTimeUtc: DateTime? = null,
    @SerializedName("period") public val period: PeriodType? = null
) {

    /**
     * Retrieve <code>used</code> field as [UsedResponse]
     */
    public fun usedResponse(): UsedResponse? = used as? UsedResponse

    /**
     * Retrieve <code>used</code> field as [Double]
     */
    public fun usedAmount(): Double? = used as? Double

    override fun toString(): String = "LimitResponse(type=$type, amount=$amount, used=$used, " +
        "untilDateTimeUtc=$untilDateTimeUtc, period=$period)"
}
