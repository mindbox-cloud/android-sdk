package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import com.google.gson.annotations.SerializedName

public open class CustomerActionRequest(
    @SerializedName("customFields") public val customFields: CustomFields? = null
)
