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
import kotlin.time.Duration.Companion.milliseconds

class SessionStorageManagerTest {
    private lateinit var mockTimeProvider: TimeProvider
    private lateinit var sessionStorageManager: SessionStorageManager

    private fun setupSessionState(
        lastTrackTime: Long,
        sessionTime: Long,
        currentTime: Long
    ) {
        sessionStorageManager.lastTrackVisitSendTime = lastTrackTime
        sessionStorageManager.sessionTime = sessionTime.milliseconds
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
            inAppMessageShownInSession.add("test1" to 1000L)
            inAppMessageShownInSession.add("test2" to 2000L)
            customerSegmentationFetchStatus = CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            geoFetchStatus = GeoFetchStatus.GEO_FETCH_SUCCESS
            processedProductSegmentations["testSystem" to "testValue"] = ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
            inAppProductSegmentations["testSystem" to "testValue"] = setOf(mockk())
            currentSessionInApps = listOf(mockk())
            shownInAppIdsWithEvents["event"] = mutableSetOf(1, 2, 3)
            configFetchingError = true
            sessionTime = 1000L.milliseconds
        }

        sessionStorageManager.clearSessionData()

        assertNull(sessionStorageManager.inAppCustomerSegmentations)
        assertTrue(sessionStorageManager.unShownOperationalInApps.isEmpty())
        assertTrue(sessionStorageManager.operationalInApps.isEmpty())
        assertTrue(sessionStorageManager.inAppMessageShownInSession.isEmpty())
        assertEquals(CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED, sessionStorageManager.customerSegmentationFetchStatus)
        assertEquals(GeoFetchStatus.GEO_NOT_FETCHED, sessionStorageManager.geoFetchStatus)
        assertTrue(sessionStorageManager.processedProductSegmentations.isEmpty())
        assertTrue(sessionStorageManager.inAppProductSegmentations.isEmpty())
        assertTrue(sessionStorageManager.currentSessionInApps.isEmpty())
        assertTrue(sessionStorageManager.shownInAppIdsWithEvents.isEmpty())
        assertFalse(sessionStorageManager.configFetchingError)
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `test inAppMessageShownInSession list operations`() {
        val inAppId1 = "inApp1"
        val inAppId2 = "inApp2"
        val time1 = 1000L
        val time2 = 2000L
        val time3 = 3000L

        assertTrue(sessionStorageManager.inAppMessageShownInSession.isEmpty())

        sessionStorageManager.inAppMessageShownInSession.add(inAppId1 to time1)
        sessionStorageManager.inAppMessageShownInSession.add(inAppId2 to time2)
        sessionStorageManager.inAppMessageShownInSession.add(inAppId1 to time3)

        assertEquals(3, sessionStorageManager.inAppMessageShownInSession.size)

        assertTrue(sessionStorageManager.inAppMessageShownInSession.contains(inAppId1 to time1))
        assertTrue(sessionStorageManager.inAppMessageShownInSession.contains(inAppId2 to time2))
        assertTrue(sessionStorageManager.inAppMessageShownInSession.contains(inAppId1 to time3))
    }

    @Test
    fun `test get last shown in-app time`() {
        val inAppId1 = "inApp1"
        val inAppId2 = "inApp2"
        val time1 = 1000L
        val time2 = 2000L
        val time3 = 3000L

        sessionStorageManager.inAppMessageShownInSession.add(inAppId1 to time1)
        sessionStorageManager.inAppMessageShownInSession.add(inAppId2 to time2)

        assertEquals(time2, sessionStorageManager.inAppMessageShownInSession.maxOfOrNull { it.second })

        sessionStorageManager.inAppMessageShownInSession.add(inAppId1 to time3)
        assertEquals(time3, sessionStorageManager.inAppMessageShownInSession.maxOfOrNull { it.second })

        sessionStorageManager.clearSessionData()
        assertNull(sessionStorageManager.inAppMessageShownInSession.maxOfOrNull { it.second })
    }
}
