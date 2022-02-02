package cloud.mindbox.mobile_sdk.models.operation

import com.google.gson.annotations.SerializedName

abstract class OperationResponseBaseInternal(
    @SerializedName("status") val status: String? = null
)
