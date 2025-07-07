package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class RetailOrderStatisticsResponse(
    @SerializedName("totalPaidAmount") public val totalPaidAmount: Double? = null
) {

    override fun toString(): String = "RetailOrderStatistics(totalPaidAmount=$totalPaidAmount)"
}
