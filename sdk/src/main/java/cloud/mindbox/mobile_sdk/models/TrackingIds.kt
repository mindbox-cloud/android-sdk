package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName

internal data class TrackingId(
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String,
)
