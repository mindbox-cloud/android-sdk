package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowFailure
import com.google.gson.annotations.SerializedName

internal data class InAppFailuresWrapper(
    @SerializedName("failures") val failures: List<InAppShowFailure>
)
