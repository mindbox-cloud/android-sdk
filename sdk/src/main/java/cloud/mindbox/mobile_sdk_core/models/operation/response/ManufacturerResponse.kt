package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

open class ManufacturerResponse(
    @SerializedName("name") val name: String? = null
) {

    override fun toString() = "ManufacturerResponse(name=$name)"

}
