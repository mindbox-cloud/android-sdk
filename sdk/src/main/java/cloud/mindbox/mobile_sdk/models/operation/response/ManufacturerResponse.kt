package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class ManufacturerResponse(
    @SerializedName("name") public val name: String? = null
) {

    override fun toString(): String = "ManufacturerResponse(name=$name)"
}
