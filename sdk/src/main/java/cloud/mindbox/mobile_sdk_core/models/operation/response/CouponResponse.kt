package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class CouponResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("pool") val pool: PoolResponse? = null
) {

    override fun toString() = "CouponResponse(ids=$ids, pool=$pool)"

}
