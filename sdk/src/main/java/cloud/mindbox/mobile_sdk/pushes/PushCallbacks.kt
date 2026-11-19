package cloud.mindbox.mobile_sdk.pushes

import com.google.gson.annotations.SerializedName

internal data class PushCallbacks(
    @SerializedName("deliveredUrl") val deliveredUrl: String?,
)
