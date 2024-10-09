package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class PromotionResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: PromotionTypeResponse? = null
) {

    override fun toString() = "PromotionResponse(ids=$ids, name=$name, type=$type)"
}
