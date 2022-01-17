package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class BalanceTypeResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null
) {

    override fun toString() = "BalanceTypeResponse(ids=$ids, name=$name)"

}
