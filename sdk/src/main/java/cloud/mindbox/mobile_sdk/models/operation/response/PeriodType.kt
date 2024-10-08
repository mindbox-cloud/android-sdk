package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class PeriodType {

    @SerializedName("FixedDays")
    FIXED_DAYS,

    @SerializedName("FixedWeeks")
    FIXED_WEEKS,

    @SerializedName("FixedMonths")
    FIXED_MONTHS
}
