package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowFailure
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class InAppFailureTrackerImplTest {

    private val timeProvider: TimeProvider = mockk()
    private val inAppRepository: InAppRepository = mockk(relaxed = true)
    private val featureToggleManager: FeatureToggleManager = mockk()
    private lateinit var inAppFailureTracker: InAppFailureTrackerImpl

    private val inAppId = "testInAppId"
    private val currentTimeMillis = 1707523200000L
    private val expectedTimestamp = "2024-02-10T00:00:00Z"

    @Before
    fun onTestStart() {
        every { timeProvider.currentTimeMillis() } returns currentTimeMillis
        inAppFailureTracker = InAppFailureTrackerImpl(
            timeProvider = timeProvider,
            inAppRepository = inAppRepository,
            featureToggleManager = featureToggleManager
        )
    }

    @Test
    fun `trackFailure does not send by default when isShouldSendImmediately is not passed`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `trackFailure sends immediately when isShouldSendImmediately is true and feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error",
            isShouldSendImmediately = true
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        val captured = slot.captured
        assertEquals(1, captured.size)
        assertEquals(inAppId, captured[0].inAppId)
        assertEquals(FailureReason.PRESENTATION_FAILED, captured[0].failureReason)
        assertEquals("error", captured[0].errorDetails)
        assertEquals(expectedTimestamp, captured[0].timestamp)
    }

    @Test
    fun `trackFailure does not send when feature toggle is disabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error",
            isShouldSendImmediately = true
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `trackFailure does not add duplicate when same inAppId already tracked`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "first",
            isShouldSendImmediately = false
        )
        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
            errorDetails = "second",
            isShouldSendImmediately = true
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        val captured = slot.captured
        assertEquals(1, captured.size)
        assertEquals(FailureReason.PRESENTATION_FAILED, captured[0].failureReason)
    }

    @Test
    fun `trackFailure truncates errorDetails to 1000 chars`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val longErrorDetails = "a".repeat(1500)
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.UNKNOWN_ERROR,
            errorDetails = longErrorDetails,
            isShouldSendImmediately = true
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals("a".repeat(1000), slot.captured[0].errorDetails)
    }

    @Test
    fun `sendAccumulatedFailures sends all failures when feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()
        inAppFailureTracker.trackFailure(
            inAppId = "inApp1",
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null,
            isShouldSendImmediately = false
        )
        inAppFailureTracker.trackFailure(
            inAppId = "inApp2",
            failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
            errorDetails = "details",
            isShouldSendImmediately = false
        )

        inAppFailureTracker.sendAccumulatedFailures()
        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        val captured = slot.captured
        assertEquals(2, captured.size)
        assertEquals(1, captured.count { it.inAppId == "inApp1" && it.failureReason == FailureReason.PRESENTATION_FAILED })
        assertEquals(1, captured.count { it.inAppId == "inApp2" && it.failureReason == FailureReason.IMAGE_DOWNLOAD_FAILED })
    }

    @Test
    fun `sendAccumulatedFailures does not send when feature toggle is disabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false
        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null,
            isShouldSendImmediately = false
        )

        inAppFailureTracker.sendAccumulatedFailures()

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `clearFailures clears accumulated failures`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null,
            isShouldSendImmediately = false
        )

        inAppFailureTracker.clearFailures()
        inAppFailureTracker.sendAccumulatedFailures()

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `trackFailure without isShouldSendImmediately accumulates failures`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()
        inAppFailureTracker.trackFailure(
            inAppId = "inApp1",
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null,
            isShouldSendImmediately = false
        )
        inAppFailureTracker.trackFailure(
            inAppId = "inApp2",
            failureReason = FailureReason.HTML_LOAD_FAILED,
            errorDetails = null,
            isShouldSendImmediately = false
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }

        inAppFailureTracker.sendAccumulatedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun `trackFailure with null errorDetails`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.trackFailure(
            inAppId = inAppId,
            failureReason = FailureReason.GEO_TARGETING_FAILED,
            errorDetails = null,
            isShouldSendImmediately = true
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals(null, slot.captured[0].errorDetails)
        assertEquals(inAppId, slot.captured[0].inAppId)
    }
}
