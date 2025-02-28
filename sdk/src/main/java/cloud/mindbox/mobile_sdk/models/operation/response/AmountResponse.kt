package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class AmountResponse(
    @SerializedName("type") public val type: AmountTypeResponse? = null,
    @SerializedName("value") public val value: Double? = null
) {

    override fun toString(): String = "AmountResponse(type=$type, value=$value)"
}
