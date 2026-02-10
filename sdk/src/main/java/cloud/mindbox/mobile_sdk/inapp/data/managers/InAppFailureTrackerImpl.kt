package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.convertToString
import cloud.mindbox.mobile_sdk.convertToZonedDateTimeAtUTC
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowFailure
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import org.threeten.bp.Instant
import java.util.concurrent.CopyOnWriteArrayList

internal class InAppFailureTrackerImpl(
    private val timeProvider: TimeProvider,
    private val inAppRepository: InAppRepository,
    private val featureToggleManager: FeatureToggleManager
) : InAppFailureTracker {

    private val failures = CopyOnWriteArrayList<InAppShowFailure>()

    private fun trackFailure(failure: InAppShowFailure) {
        if (failures.none { it.inAppId == failure.inAppId }) {
            failures.add(failure)
        }
    }

    private fun sendFailures() {
        if (!featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE)) return
        if (failures.isNotEmpty()) inAppRepository.sendInAppShowFailure(failures.toList())
        failures.clear()
    }

    private fun sendSingleFailure(failure: InAppShowFailure) {
        if (!featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE)) return
        inAppRepository.sendInAppShowFailure(listOf(failure))
    }

    override fun sendFailure(inAppId: String, failureReason: FailureReason, errorDetails: String?) {
        val timestamp = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
            .convertToZonedDateTimeAtUTC()
            .convertToString()

        sendSingleFailure(
            failure = InAppShowFailure(
                inAppId = inAppId,
                failureReason = failureReason,
                errorDetails = errorDetails?.take(COUNT_OF_CHARS_IN_ERROR_DETAILS),
                timestamp = timestamp
            )
        )
    }

    override fun collectFailure(inAppId: String, failureReason: FailureReason, errorDetails: String?) {
        val timestamp = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
            .convertToZonedDateTimeAtUTC()
            .convertToString()
        trackFailure(
            InAppShowFailure(
                inAppId = inAppId,
                failureReason = failureReason,
                errorDetails = errorDetails?.take(COUNT_OF_CHARS_IN_ERROR_DETAILS),
                timestamp = timestamp
            )
        )
    }

    override fun sendCollectedFailures() {
        sendFailures()
    }

    override fun clearFailures() {
        failures.clear()
    }

    companion object {
        private const val COUNT_OF_CHARS_IN_ERROR_DETAILS = 1000
    }
}
