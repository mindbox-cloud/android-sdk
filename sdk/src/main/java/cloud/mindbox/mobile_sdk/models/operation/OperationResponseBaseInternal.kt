package cloud.mindbox.mobile_sdk.models.operation

import com.google.gson.annotations.SerializedName

public abstract class OperationResponseBaseInternal(
    @SerializedName("status") public val status: String? = null,
)
