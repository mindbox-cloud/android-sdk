package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class PromotionRequest(
    @SerializedName("ids") public val ids: Ids? = null
)
