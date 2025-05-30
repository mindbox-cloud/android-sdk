package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
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
        sessionStorageManager.lastTrackVisitSendTime.set(lastTrackTime)
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

        assertEquals(0L, sessionStorageManager.lastTrackVisitSendTime.get())
        sessionStorageManager.hasSessionExpired()
        assertEquals(1000L, sessionStorageManager.lastTrackVisitSendTime.get())
    }

    @Test
    fun `hasSessionExpired should not check expiration if sessionTime is 0`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 0L, currentTime = 2000L)

        sessionStorageManager.hasSessionExpired()

        verify(exactly = 0) { listener.invoke() }
        assertEquals(2000L, sessionStorageManager.lastTrackVisitSendTime.get())
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

        assertEquals(sessionStorageManager.lastTrackVisitSendTime.get(), 1500L)
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
        assertFalse(sessionStorageManager.isInAppMessageShown)
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
    fun `hasSessionExpired should not have race conditions on lastTrackVisitSendTime`() {
        val listener = mockk<() -> Unit>()
        val threads = mutableListOf<Thread>()
        sessionStorageManager.addSessionExpirationListener(listener)
        setupSessionState(lastTrackTime = 1000L, sessionTime = 999L, currentTime = 2000L)

        repeat(5) {
            threads.add(Thread {
                sessionStorageManager.hasSessionExpired()
            })
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        verify(exactly = 1) { listener.invoke() }
    }
}
