package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionState
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.models.Timestamp
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MaxInappsPerSessionLimitCheckerTest {

    private lateinit var sessionStorageManager: SessionStorageManager

    private val sessionState = SessionState()
    private lateinit var maxInappsPerSessionLimitChecker: MaxInappsPerSessionLimitChecker

    @Before
    fun setup() {
        sessionStorageManager = mockk()
        every { sessionStorageManager.state } returns sessionState
        maxInappsPerSessionLimitChecker = MaxInappsPerSessionLimitChecker(sessionStorageManager)
    }

    @Test
    fun `check returns true when setting maxInappsPerSession is null`() {
        val shownInapps = mutableListOf("inapp1", "inapp2")
        sessionStorageManager.state.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = null,
        )
        sessionStorageManager.state.inAppMessageShownInSession = shownInapps

        val result = maxInappsPerSessionLimitChecker.check(emptyList())

        assertTrue(result)
    }

    @Test
    fun `check returns true when shown inapps count is less than limit`() {
        val maxInappsPerSession = 3
        val shownInapps = mutableListOf("inapp1", "inapp2")

        sessionStorageManager.state.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = maxInappsPerSession
        )
        sessionStorageManager.state.inAppMessageShownInSession = shownInapps

        val result = maxInappsPerSessionLimitChecker.check(emptyList())

        assertTrue(result)
    }

    @Test
    fun `check returns false when shown inapps count equals limit`() {
        val maxInappsPerSession = 2
        val shownInapps = mutableListOf("inapp1", "inapp2")

        sessionStorageManager.state.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = maxInappsPerSession
        )
        sessionStorageManager.state.inAppMessageShownInSession = shownInapps

        val result = maxInappsPerSessionLimitChecker.check(emptyList())

        assertFalse(result)
    }

    @Test
    fun `check returns false when shown inapps count exceeds limit`() {
        val maxInappsPerSession = 1
        val shownInapps = mutableListOf("inapp1", "inapp2")

        sessionStorageManager.state.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = maxInappsPerSession
        )
        sessionStorageManager.state.inAppMessageShownInSession = shownInapps

        val result = maxInappsPerSessionLimitChecker.check(emptyList())

        assertFalse(result)
    }

    @Test
    fun `check counts held reservations against the session limit`() {
        sessionStorageManager.state.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = 2
        )
        sessionStorageManager.state.inAppMessageShownInSession = mutableListOf("inapp1")
        val held = listOf(ShowReservation("inapp2", Timestamp(1L)))

        assertFalse(maxInappsPerSessionLimitChecker.check(held))
        assertTrue(maxInappsPerSessionLimitChecker.check(emptyList()))
    }
}
