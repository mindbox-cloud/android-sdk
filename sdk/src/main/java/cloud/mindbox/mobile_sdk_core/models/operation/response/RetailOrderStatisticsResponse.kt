package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

open class RetailOrderStatisticsResponse(
    @SerializedName("totalPaidAmount") val totalPaidAmount: Double? = null
) {

    override fun toString() = "RetailOrderStatistics(totalPaidAmount=$totalPaidAmount)"

}