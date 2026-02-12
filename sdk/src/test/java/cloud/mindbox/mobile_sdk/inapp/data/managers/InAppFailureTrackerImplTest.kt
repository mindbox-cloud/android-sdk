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
    fun `collectFailure does not send immediately`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `sendFailure sends immediately when feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
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
    fun `sendFailure does not send when feature toggle is disabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `collectFailure does not add duplicate when same inAppId already tracked`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "first"
        )
        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
            errorDetails = "second"
        )
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        val captured = slot.captured
        assertEquals(1, captured.size)
        assertEquals(FailureReason.PRESENTATION_FAILED, captured[0].failureReason)
    }

    @Test
    fun `sendFailure truncates errorDetails to 1000 chars`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val longErrorDetails = "a".repeat(1500)
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.UNKNOWN_ERROR,
            errorDetails = longErrorDetails
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals("a".repeat(1000), slot.captured[0].errorDetails)
    }

    @Test
    fun `collectFailure truncates errorDetails to 1000 chars`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val longErrorDetails = "a".repeat(1500)
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.UNKNOWN_ERROR,
            errorDetails = longErrorDetails
        )
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals("a".repeat(1000), slot.captured[0].errorDetails)
    }

    @Test
    fun `sendCollectedFailures sends all failures when feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.collectFailure(
            inAppId = "inApp1",
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null
        )
        inAppFailureTracker.collectFailure(
            inAppId = "inApp2",
            failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
            errorDetails = "details"
        )

        inAppFailureTracker.sendCollectedFailures()
        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        val captured = slot.captured
        assertEquals(2, captured.size)
        assertEquals(1, captured.count { it.inAppId == "inApp1" && it.failureReason == FailureReason.PRESENTATION_FAILED })
        assertEquals(1, captured.count { it.inAppId == "inApp2" && it.failureReason == FailureReason.IMAGE_DOWNLOAD_FAILED })
    }

    @Test
    fun `sendCollectedFailures clears failures after sending`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null
        )
        inAppFailureTracker.sendCollectedFailures()
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `sendCollectedFailures does not send when feature toggle is disabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null
        )
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `clearFailures clears collected failures`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null
        )

        inAppFailureTracker.clearFailures()
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 0) { inAppRepository.sendInAppShowFailure(any()) }
    }

    @Test
    fun `sendFailure with null errorDetails`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowFailure>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.GEO_TARGETING_FAILED,
            errorDetails = null
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowFailure(capture(slot)) }
        assertEquals(null, slot.captured[0].errorDetails)
        assertEquals(inAppId, slot.captured[0].inAppId)
    }
}
