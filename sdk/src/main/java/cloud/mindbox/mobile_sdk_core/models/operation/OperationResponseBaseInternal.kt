package cloud.mindbox.mobile_sdk_core.models.operation

import com.google.gson.annotations.SerializedName

abstract class OperationResponseBaseInternal(
    @SerializedName("status") val status: String? = null
)
