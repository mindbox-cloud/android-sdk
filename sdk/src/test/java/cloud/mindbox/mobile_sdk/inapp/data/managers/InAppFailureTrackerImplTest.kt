package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.operation.request.EmbeddedBlockShowFailure
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowError
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

    private fun List<InAppShowError>.asFailures(): List<InAppShowFailure> = filterIsInstance<InAppShowFailure>()

    @Before
    fun onTestStart() {
        every { timeProvider.currentTimeMillis() } returns currentTimeMillis
        inAppFailureTracker = InAppFailureTrackerImpl(
            timeProvider = timeProvider,
            inAppRepository = inAppRepository,
            featureToggleManager = featureToggleManager,
            sessionStorageManager = SessionStorageManager(timeProvider),
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

        verify(exactly = 0) { inAppRepository.sendInAppShowErrors(any()) }
    }

    @Test
    fun `sendFailure sends immediately when feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        val captured = slot.captured.asFailures()
        assertEquals(1, captured.size)
        assertEquals(inAppId, captured[0].inAppId)
        assertEquals(FailureReason.PRESENTATION_FAILED, captured[0].failureReason)
        assertEquals("error", captured[0].errorDetails)
        assertEquals(expectedTimestamp, captured[0].dateTimeUtc)
    }

    @Test
    fun `sendFailure does not send when feature toggle is disabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error"
        )

        verify(exactly = 0) { inAppRepository.sendInAppShowErrors(any()) }
    }

    @Test
    fun `collectFailure does not add duplicate when same inAppId already tracked`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowError>>()

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

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        val captured = slot.captured.asFailures()
        assertEquals(1, captured.size)
        assertEquals(FailureReason.PRESENTATION_FAILED, captured[0].failureReason)
    }

    @Test
    fun `sendFailure truncates errorDetails to 1000 chars`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val longErrorDetails = "a".repeat(1500)
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.UNKNOWN_ERROR,
            errorDetails = longErrorDetails
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        assertEquals("a".repeat(1000), slot.captured.asFailures()[0].errorDetails)
    }

    @Test
    fun `collectFailure truncates errorDetails to 1000 chars`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val longErrorDetails = "a".repeat(1500)
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.collectFailure(
            inAppId = inAppId,
            failureReason = FailureReason.UNKNOWN_ERROR,
            errorDetails = longErrorDetails
        )
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        assertEquals("a".repeat(1000), slot.captured.asFailures()[0].errorDetails)
    }

    @Test
    fun `sendCollectedFailures sends all failures when feature toggle is enabled`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowError>>()

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
        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        val captured = slot.captured.asFailures()
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

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(any()) }
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

        verify(exactly = 0) { inAppRepository.sendInAppShowErrors(any()) }
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

        verify(exactly = 0) { inAppRepository.sendInAppShowErrors(any()) }
    }

    @Test
    fun `sendFailure forwards tags to the failure`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val tags = mapOf("templateType" to "Popup")
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = "error",
            tags = tags
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        assertEquals(tags, slot.captured.asFailures()[0].tags)
    }

    @Test
    fun `collectFailure keeps each failures own tags`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.collectFailure(
            inAppId = "inApp1",
            failureReason = FailureReason.PRESENTATION_FAILED,
            errorDetails = null,
            tags = mapOf("templateType" to "Popup")
        )
        inAppFailureTracker.collectFailure(
            inAppId = "inApp2",
            failureReason = FailureReason.IMAGE_DOWNLOAD_FAILED,
            errorDetails = null,
            tags = null
        )
        inAppFailureTracker.sendCollectedFailures()

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        val captured = slot.captured.asFailures()
        assertEquals(mapOf("templateType" to "Popup"), captured.first { it.inAppId == "inApp1" }.tags)
        assertEquals(null, captured.first { it.inAppId == "inApp2" }.tags)
    }

    @Test
    fun `sendFailure with null errorDetails`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val slot = slot<List<InAppShowError>>()

        inAppFailureTracker.sendFailure(
            inAppId = inAppId,
            failureReason = FailureReason.GEO_TARGETING_FAILED,
            errorDetails = null
        )

        verify(exactly = 1) { inAppRepository.sendInAppShowErrors(capture(slot)) }
        assertEquals(null, slot.captured.asFailures()[0].errorDetails)
        assertEquals(inAppId, slot.captured.asFailures()[0].inAppId)
    }

    @Test
    fun `sendWaitBudgetExceeded ships the place-named fact once per place a session`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns true
        val sent = mutableListOf<List<InAppShowError>>()

        inAppFailureTracker.sendWaitBudgetExceeded("main-screen-top", Milliseconds(30_000L), WaitBudgetPhase.CONFIG_MISSING)
        inAppFailureTracker.sendWaitBudgetExceeded("main-screen-top", Milliseconds(30_000L), WaitBudgetPhase.RESOLVE_PENDING)
        inAppFailureTracker.sendWaitBudgetExceeded("another-place", Milliseconds(30_000L), WaitBudgetPhase.RESOLVE_PENDING)

        verify(exactly = 2) { inAppRepository.sendInAppShowErrors(capture(sent)) }
        val first = sent.first().single() as EmbeddedBlockShowFailure
        assertEquals("main-screen-top", first.placeSystemName)
        assertEquals("phase=config_missing; waited=00:00:30.0000000", first.errorDetails)
        val failure = sent.last().single() as EmbeddedBlockShowFailure
        assertEquals("another-place", failure.placeSystemName)
        assertEquals(FailureReason.WAIT_BUDGET_EXCEEDED, failure.failureReason)
        assertEquals("phase=resolve_pending; waited=00:00:30.0000000", failure.errorDetails)
        assertEquals(expectedTimestamp, failure.dateTimeUtc)
    }

    @Test
    fun `sendWaitBudgetExceeded respects the feature toggle`() {
        every { featureToggleManager.isEnabled(SEND_INAPP_SHOW_ERROR_FEATURE) } returns false

        inAppFailureTracker.sendWaitBudgetExceeded("main-screen-top", Milliseconds(30_000L), WaitBudgetPhase.CONFIG_MISSING)

        verify(exactly = 0) { inAppRepository.sendInAppShowErrors(any()) }
    }
}
