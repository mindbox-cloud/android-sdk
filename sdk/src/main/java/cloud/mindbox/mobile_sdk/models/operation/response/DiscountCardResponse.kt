package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class DiscountCardResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("status") public val status: StatusResponse? = null,
    @SerializedName("type") public val type: TypeResponse? = null
) {

    override fun toString(): String = "DiscountCardResponse(ids=$ids, customFields=$customFields, status=$status, type=$type)"
}
