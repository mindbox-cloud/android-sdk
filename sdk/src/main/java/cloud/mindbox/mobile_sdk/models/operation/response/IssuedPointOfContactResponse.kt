package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class IssuedPointOfContactResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("name") public val name: String? = null
) {

    override fun toString(): String = "IssuedPointOfContactResponse(ids=$ids, name=$name)"
}
