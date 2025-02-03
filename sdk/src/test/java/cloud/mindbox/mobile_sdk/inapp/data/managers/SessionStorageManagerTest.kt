package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionStorageManagerTest {
    private lateinit var mockTimeProvider: TimeProvider
    private lateinit var sessionStorageManager: SessionStorageManager

    private fun setupSessionState(
        lastTrackTime: Long,
        sessionTime: Long,
        currentTime: Long
    ) {
        sessionStorageManager.lastTrackVisitSendTime = lastTrackTime
        sessionStorageManager.sessionTime = sessionTime
        every { mockTimeProvider.currentTimeMillis() } returns currentTime
    }

    @Before
    fun setup() {
        mockTimeProvider = mockk()
        sessionStorageManager = SessionStorageManager(mockTimeProvider)
    }

    @Test
    fun `hasSessionExpired should initialize lastTrackVisitSendTime on first call`() {
        every { mockTimeProvider.currentTimeMillis() } returns 1000L

        assertEquals(0L, sessionStorageManager.lastTrackVisitSendTime)
        sessionStorageManager.hasSessionExpired()
        assertEquals(1000L, sessionStorageManager.lastTrackVisitSendTime)
    }

    @Test
    fun `hasSessionExpired should not check expiration if sessionTime is 0`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 0L, currentTime = 2000L)

        sessionStorageManager.hasSessionExpired()

        verify(exactly = 0) { listener.invoke() }
        assertEquals(2000L, sessionStorageManager.lastTrackVisitSendTime)
    }

    @Test
    fun `hasSessionExpired should notify listeners when session expired`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 999L, currentTime = 2000L)

        sessionStorageManager.hasSessionExpired()

        verify(exactly = 1) { listener.invoke() }
    }

    @Test
    fun `hasSessionExpired should not notify listeners when session is active`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 2000L, currentTime = 1500L)

        sessionStorageManager.hasSessionExpired()

        verify(exactly = 0) { listener.invoke() }
    }

    @Test
    fun `hasSessionExpired should not notify listeners when session is active in case when sessionTime equals time between visits`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 2000L, currentTime = 3000L)

        sessionStorageManager.hasSessionExpired()

        verify(exactly = 0) { listener.invoke() }
    }

    @Test
    fun `hasSessionExpired should not notify listeners when sessionTime is negative`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = -2000L, currentTime = 1500L)

        sessionStorageManager.hasSessionExpired()

        assertEquals(sessionStorageManager.lastTrackVisitSendTime, 1500L)
        verify(exactly = 0) { listener.invoke() }
    }

    @Test
    fun `clearSessionData should reset all fields to default values`() {
        sessionStorageManager.apply {
            inAppCustomerSegmentations = mockk()
            unShownOperationalInApps["test"] = mutableListOf(mockk())
            operationalInApps["test"] = mutableListOf(mockk())
            isInAppMessageShown = true
            customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            geoFetchStatus = GeoFetchStatus.GEO_FETCH_SUCCESS
            productSegmentationFetchStatus = ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            inAppProductSegmentations["test"] = setOf(mockk())
            currentSessionInApps = listOf(mockk())
            shownInAppIdsWithEvents["event"] = mutableSetOf(1, 2, 3)
            configFetchingError = true
            sessionTime = 1000L
        }

        sessionStorageManager.clearSessionData()

        assertNull(sessionStorageManager.inAppCustomerSegmentations)
        assertTrue(sessionStorageManager.unShownOperationalInApps.isEmpty())
        assertTrue(sessionStorageManager.operationalInApps.isEmpty())
        assertFalse(sessionStorageManager.isInAppMessageShown)
        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED, sessionStorageManager.customerSegmentationFetchStatus)
        assertEquals(GeoFetchStatus.GEO_NOT_FETCHED, sessionStorageManager.geoFetchStatus)
        assertEquals(ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED, sessionStorageManager.productSegmentationFetchStatus)
        assertTrue(sessionStorageManager.inAppProductSegmentations.isEmpty())
        assertTrue(sessionStorageManager.currentSessionInApps.isEmpty())
        assertTrue(sessionStorageManager.shownInAppIdsWithEvents.isEmpty())
        assertFalse(sessionStorageManager.configFetchingError)
        assertEquals(0L, sessionStorageManager.sessionTime)
    }
}
