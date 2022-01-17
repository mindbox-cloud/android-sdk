package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

abstract class OperationResponseBase(
    @SerializedName("status") val status: String? = null
)
