package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class UsedPointOfContactResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("name") public val name: String? = null
) {

    override fun toString(): String = "UsedPointOfContactResponse(ids=$ids, name=$name)"
}
