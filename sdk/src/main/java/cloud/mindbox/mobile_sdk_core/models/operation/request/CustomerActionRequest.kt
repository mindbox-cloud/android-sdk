package cloud.mindbox.mobile_sdk_core.models.operation.request

import cloud.mindbox.mobile_sdk_core.models.operation.CustomFields
import com.google.gson.annotations.SerializedName

open class CustomerActionRequest(
    @SerializedName("customFields") val customFields: CustomFields? = null
)
