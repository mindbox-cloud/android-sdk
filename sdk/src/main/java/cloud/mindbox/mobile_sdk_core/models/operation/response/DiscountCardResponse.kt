package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.CustomFields
import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class DiscountCardResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("status") val status: StatusResponse? = null,
    @SerializedName("type") val type: TypeResponse? = null
) {

    override fun toString() = "DiscountCardResponse(ids=$ids, customFields=$customFields, status=$status, type=$type)"

}
