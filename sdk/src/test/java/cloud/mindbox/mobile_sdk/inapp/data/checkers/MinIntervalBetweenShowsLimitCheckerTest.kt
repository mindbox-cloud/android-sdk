package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.inapp.domain.models.ShowReservation
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MinIntervalBetweenShowsLimitCheckerTest {

    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var inAppRepository: InAppRepository
    private lateinit var timeProvider: TimeProvider
    private lateinit var minIntervalBetweenShowsLimitChecker: MinIntervalBetweenShowsLimitChecker

    @Before
    fun setup() {
        sessionStorageManager = mockk()
        inAppRepository = mockk()
        timeProvider = mockk()
        minIntervalBetweenShowsLimitChecker = MinIntervalBetweenShowsLimitChecker(
            sessionStorageManager = sessionStorageManager,
            inAppRepository = inAppRepository,
            timeProvider = timeProvider
        )
    }

    @Test
    fun `check returns true when minIntervalBetweenShows is null`() {
        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = null
        )

        val result = minIntervalBetweenShowsLimitChecker.check(emptyList())

        assertTrue(result)
    }

    @Test
    fun `check returns false when current time is less than last show time plus interval`() {
        val lastShowTime = 1000L
        val currentTime = 2000L
        val interval = Milliseconds(2000L)

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = interval
        )
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(lastShowTime)
        every { timeProvider.currentTimestamp() } returns Timestamp(currentTime)

        val result = minIntervalBetweenShowsLimitChecker.check(emptyList())

        assertFalse(result)
    }

    @Test
    fun `check returns false when current time equals last show time plus interval`() {
        val lastShowTime = 1000L
        val currentTime = 3000L
        val interval = Milliseconds(2000L)

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = interval
        )
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(lastShowTime)
        every { timeProvider.currentTimestamp() } returns Timestamp(currentTime)

        val result = minIntervalBetweenShowsLimitChecker.check(emptyList())

        assertFalse(result)
    }

    @Test
    fun `check returns true when current time is greater than last show time plus interval`() {
        val lastShowTime = 1000L
        val currentTime = 4000L
        val interval = Milliseconds(2000L)

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = interval
        )
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(lastShowTime)
        every { timeProvider.currentTimestamp() } returns Timestamp(currentTime)

        val result = minIntervalBetweenShowsLimitChecker.check(emptyList())

        assertTrue(result)
    }

    @Test
    fun `check counts the newest reservation as the last show`() {
        val interval = Milliseconds(2000L)
        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = interval
        )
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(0L)
        every { timeProvider.currentTimestamp() } returns Timestamp(10_000L)

        val fresh = listOf(ShowReservation("place|main", "inapp1", Timestamp(9_000L)))
        val stale = listOf(ShowReservation("place|main", "inapp1", Timestamp(7_000L)))

        assertFalse(minIntervalBetweenShowsLimitChecker.check(fresh))
        assertTrue(minIntervalBetweenShowsLimitChecker.check(stale))
    }

    @Test
    fun `check keeps the dismiss time when it is newer than every reservation`() {
        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            minIntervalBetweenShows = Milliseconds(2000L)
        )
        every { inAppRepository.getLastInappDismissTime() } returns Timestamp(9_000L)
        every { timeProvider.currentTimestamp() } returns Timestamp(10_000L)

        val old = listOf(ShowReservation("place|main", "inapp1", Timestamp(1_000L)))

        assertFalse(minIntervalBetweenShowsLimitChecker.check(old))
    }
}
