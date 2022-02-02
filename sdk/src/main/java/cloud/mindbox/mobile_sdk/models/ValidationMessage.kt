package cloud.mindbox.mobile_sdk.models

import com.google.gson.annotations.SerializedName

data class ValidationMessage(
    @SerializedName("message") val message: String? = null,
    @SerializedName("location") val location: String? = null,
)