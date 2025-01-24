package cloud.mindbox.mobile_sdk.inapp.data.managers

import io.mockk.*
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class SessionStorageManagerTest {

    private lateinit var sessionStorageManager: SessionStorageManager

    @Before
    fun setup() {
        sessionStorageManager = SessionStorageManager()
    }

    @Test
    fun `hasSessionExpired should initialize lastTrackVisitSendTime on first call`() {
        assertEquals(0L, sessionStorageManager.lastTrackVisitSendTime)
        sessionStorageManager.hasSessionExpired(1000L)
        assertEquals(1000L, sessionStorageManager.lastTrackVisitSendTime)
    }

    @Test
    fun `hasSessionExpired should not check expiration if sessionTime is 0`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        sessionStorageManager.sessionTime = 0L
        sessionStorageManager.lastTrackVisitSendTime = 1000L

        sessionStorageManager.hasSessionExpired(2000L)

        verify(exactly = 0) { listener.invoke() }
        assertEquals(2000L, sessionStorageManager.lastTrackVisitSendTime)
    }

    @Test
    fun `hasSessionExpired should notify listeners when session expired`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        sessionStorageManager.lastTrackVisitSendTime = 1000L
        sessionStorageManager.sessionTime = 999L

        sessionStorageManager.hasSessionExpired(2000L)

        verify(exactly = 1) { listener.invoke() }
    }

    @Test
    fun `hasSessionExpired should not notify listeners when session is active`() {
        val listener = mockk<() -> Unit>()
        sessionStorageManager.addSessionExpirationListener(listener)
        sessionStorageManager.lastTrackVisitSendTime = 1000L
        sessionStorageManager.sessionTime = 2000L

        sessionStorageManager.hasSessionExpired(1500L)

        verify(exactly = 0) { listener.invoke() }
    }
}
