package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class AmountResponse(
    @SerializedName("type") val type: AmountTypeResponse? = null,
    @SerializedName("value") val value: Double? = null
) {

    override fun toString() = "AmountResponse(type=$type, value=$value)"
}
