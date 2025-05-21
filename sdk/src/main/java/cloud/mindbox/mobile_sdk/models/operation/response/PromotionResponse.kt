package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class PromotionResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("name") public val name: String? = null,
    @SerializedName("type") public val type: PromotionTypeResponse? = null
) {

    override fun toString(): String = "PromotionResponse(ids=$ids, name=$name, type=$type)"
}
