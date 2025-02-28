package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class ProductGroupResponse(
    @SerializedName("ids") public val ids: Ids? = null
) {
    override fun toString(): String = "ProductGroupResponse(ids=$ids)"
}
