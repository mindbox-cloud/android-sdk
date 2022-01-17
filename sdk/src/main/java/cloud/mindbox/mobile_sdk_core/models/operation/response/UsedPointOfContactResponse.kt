package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class UsedPointOfContactResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null
) {

    override fun toString() = "UsedPointOfContactResponse(ids=$ids, name=$name)"

}
