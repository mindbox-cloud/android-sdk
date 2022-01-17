package cloud.mindbox.mobile_sdk_core.models.operation.request

import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class CouponRequest(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("pool") val pool: PoolRequest? = null
)
