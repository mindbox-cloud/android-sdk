package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class UsedResponse(
    @SerializedName("usageServiceStatus") public val usageServiceStatus: UsageServiceStatusResponse? = null,
    @SerializedName("amount") public val amount: Double? = null
) {

    override fun toString(): String = "UsedResponse(usageServiceStatus=$usageServiceStatus, amount=$amount)"
}
