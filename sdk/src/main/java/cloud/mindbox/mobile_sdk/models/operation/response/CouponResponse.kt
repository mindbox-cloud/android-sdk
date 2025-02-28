package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class CouponResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("pool") public val pool: PoolResponse? = null
) {

    override fun toString(): String = "CouponResponse(ids=$ids, pool=$pool)"
}
