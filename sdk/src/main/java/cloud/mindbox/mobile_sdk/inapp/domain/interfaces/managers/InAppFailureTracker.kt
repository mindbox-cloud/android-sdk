package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

internal interface InAppFailureTracker {

    fun trackFailure(
        inAppId: String,
        failureReason: FailureReason,
        errorDetails: String?,
        isShouldSendImmediately: Boolean = false
    )

    fun sendAccumulatedFailures()

    fun clearFailures()
}
