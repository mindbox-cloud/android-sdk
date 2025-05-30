package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class ExternalPromoActionRequest(
    @SerializedName("ids") public val ids: Ids? = null
)
