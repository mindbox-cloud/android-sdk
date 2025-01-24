package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.*
import org.junit.Assert.assertEquals
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
}
