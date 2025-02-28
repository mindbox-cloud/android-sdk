package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class PoolResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("name") public val name: String? = null,
    @SerializedName("description") public val description: String? = null
) {

    override fun toString(): String = "PoolResponse(ids=$ids, name=$name, description=$description)"
}
