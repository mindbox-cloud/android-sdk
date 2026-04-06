package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowFailure

internal data class InAppFailuresWrapper(
    val failures: List<InAppShowFailure>
)
