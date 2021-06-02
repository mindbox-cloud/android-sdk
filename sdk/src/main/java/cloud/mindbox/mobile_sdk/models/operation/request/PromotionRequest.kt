package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class PromotionRequest(
    @SerializedName("ids") val ids: Ids? = null
)
