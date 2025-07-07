package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class PoolResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null
) {

    override fun toString() = "PoolResponse(ids=$ids, name=$name, description=$description)"
}
