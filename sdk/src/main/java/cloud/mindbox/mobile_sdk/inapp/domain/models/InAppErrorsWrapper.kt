package cloud.mindbox.mobile_sdk.inapp.domain.models

import com.google.gson.annotations.SerializedName
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowError

internal data class InAppErrorsWrapper(
    @SerializedName("errors")
    val errors: List<InAppShowError>
)
