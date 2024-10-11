package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class IssuedPointOfContactResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null
) {

    override fun toString() = "IssuedPointOfContactResponse(ids=$ids, name=$name)"
}
