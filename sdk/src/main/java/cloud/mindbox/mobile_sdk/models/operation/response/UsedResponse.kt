package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class UsedResponse(
    @SerializedName("usageServiceStatus") val usageServiceStatus: UsageServiceStatusResponse? = null,
    @SerializedName("amount") val amount: Double? = null
) {

    override fun toString() = "UsedResponse(usageServiceStatus=$usageServiceStatus, amount=$amount)"

}
