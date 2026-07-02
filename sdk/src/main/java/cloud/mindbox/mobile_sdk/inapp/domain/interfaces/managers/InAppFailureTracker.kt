package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

internal interface InAppFailureTracker {

    fun sendFailure(
        inAppId: String,
        failureReason: FailureReason,
        errorDetails: String?,
        tags: Map<String, String>? = null
    )

    fun collectFailure(
        inAppId: String,
        failureReason: FailureReason,
        errorDetails: String?,
        tags: Map<String, String>? = null
    )

    fun sendCollectedFailures()

    fun clearFailures()
}
