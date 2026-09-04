package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.convertToString
import cloud.mindbox.mobile_sdk.convertToZonedDateTimeAtUTC
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.millisToTimeSpan
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.operation.request.EmbeddedBlockShowFailure
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowFailure
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import org.threeten.bp.Instant
import java.util.concurrent.CopyOnWriteArrayList

internal class InAppFailureTrackerImpl(
    private val timeProvider: TimeProvider,
    private val inAppRepository: InAppRepository,
    private val featureToggleManager: FeatureToggleManager,
    private val sessionStorageManager: SessionStorageManager,
) : InAppFailureTracker {

    private val failures = CopyOnWriteArrayList<InAppShowFailure>()

    private fun trackFailure(failure: InAppShowFailure) {
        if (failures.none { it.inAppId == failure.inAppId }) {
            failures.add(failure)
        }
    }

    private fun sendFailures() {
        if (failures.isEmpty()) return
        if (!featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE)) {
            mindboxLogI("Feature $SEND_INAPP_SHOW_ERROR_FEATURE is off. Skip send failures")
            return
        }
        val toSend = failures.toList()
        inAppRepository.sendInAppShowErrors(toSend)
        toSend.forEach { failure -> sessionStorageManager.reportedShowFailures.add(failure.sessionKey()) }
        failures.clear()
    }

    private fun InAppShowFailure.sessionKey(): String = sessionKey(inAppId, failureReason)

    private fun sessionKey(inAppId: String, failureReason: FailureReason): String = "$inAppId|${failureReason.name}"

    private fun sendSingleFailure(failure: InAppShowFailure) {
        if (!featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE)) {
            mindboxLogI("Feature $SEND_INAPP_SHOW_ERROR_FEATURE is off. Skip send failure")
            return
        }
        inAppRepository.sendInAppShowErrors(listOf(failure))
    }

    override fun sendFailure(
        inAppId: String,
        failureReason: FailureReason,
        errorDetails: String?,
        tags: Map<String, String>?
    ) {
        val timestamp = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
            .convertToZonedDateTimeAtUTC()
            .convertToString()

        sendSingleFailure(
            failure = InAppShowFailure(
                inAppId = inAppId,
                failureReason = failureReason,
                errorDetails = errorDetails?.take(COUNT_OF_CHARS_IN_ERROR_DETAILS),
                dateTimeUtc = timestamp,
                tags = tags
            )
        )
    }

    override fun collectFailure(
        inAppId: String,
        failureReason: FailureReason,
        errorDetails: String?,
        tags: Map<String, String>?
    ) {
        if (sessionKey(inAppId, failureReason) in sessionStorageManager.reportedShowFailures) {
            mindboxLogI("Failure $failureReason for in-app $inAppId already reported this session, not collecting it again")
            return
        }
        val timestamp = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
            .convertToZonedDateTimeAtUTC()
            .convertToString()
        trackFailure(
            InAppShowFailure(
                inAppId = inAppId,
                failureReason = failureReason,
                errorDetails = errorDetails?.take(COUNT_OF_CHARS_IN_ERROR_DETAILS),
                dateTimeUtc = timestamp,
                tags = tags
            )
        )
    }

    override fun sendCollectedFailures() {
        sendFailures()
    }

    override fun clearFailures() {
        failures.clear()
    }

    override fun sendWaitBudgetExceeded(placeSystemName: String, waitedFor: Milliseconds, phase: WaitBudgetPhase) {
        if (!featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE)) {
            mindboxLogI("Feature $SEND_INAPP_SHOW_ERROR_FEATURE is off. Skip send wait budget failure")
            return
        }
        if (!sessionStorageManager.waitBudgetReportedPlaces.add(placeSystemName)) {
            mindboxLogI("Place '$placeSystemName' already reported its exceeded wait budget this session")
            return
        }
        mindboxLogI("The SDK stayed silent past the block's budget, sending the place-named failure")
        val timestamp = Instant.ofEpochMilli(timeProvider.currentTimeMillis())
            .convertToZonedDateTimeAtUTC()
            .convertToString()
        inAppRepository.sendInAppShowErrors(
            listOf(
                EmbeddedBlockShowFailure(
                    placeSystemName = placeSystemName,
                    failureReason = FailureReason.WAIT_BUDGET_EXCEEDED,
                    errorDetails = "phase=${phase.wireName}; waited=${waitedFor.interval.millisToTimeSpan()}",
                    dateTimeUtc = timestamp,
                )
            )
        )
    }

    companion object {
        private const val COUNT_OF_CHARS_IN_ERROR_DETAILS = 1000
    }
}
