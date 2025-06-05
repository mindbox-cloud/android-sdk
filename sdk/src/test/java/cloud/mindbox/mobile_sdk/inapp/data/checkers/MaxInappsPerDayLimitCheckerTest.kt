package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MaxInappsPerDayLimitCheckerTest {

    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var inAppRepository: InAppRepository
    private lateinit var timeProvider: TimeProvider
    private lateinit var maxInappsPerDayLimitChecker: MaxInappsPerDayLimitChecker

    companion object {
        // 2024-03-26 12:00:00 UTC
        private const val TEST_TIME = 1711454400000L
        private const val ONE_HOUR_MS = 3600000L
        private const val TWO_HOURS_MS = 7200000L
        private const val ONE_DAY_MS = 86400000L
    }

    @Before
    fun setup() {
        sessionStorageManager = mockk()
        inAppRepository = mockk()
        timeProvider = mockk()
        maxInappsPerDayLimitChecker = MaxInappsPerDayLimitChecker(
            inAppRepository = inAppRepository,
            sessionStorageManager = sessionStorageManager,
            timeProvider = timeProvider
        )
    }

    @Test
    fun `check returns true when setting maxInappsPerDay is null`() {
        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            maxInappsPerDay = null
        )
        every { timeProvider.currentTimeMillis() } returns TEST_TIME
        every { inAppRepository.getShownInApps() } returns mapOf(
            "inapp1" to listOf(TEST_TIME - ONE_HOUR_MS)
        )

        val result = maxInappsPerDayLimitChecker.check()

        assertTrue(result)
    }

    @Test
    fun `check returns true when shown inapps count is less than limit`() {
        val maxInappsPerDay = 3

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            maxInappsPerDay = maxInappsPerDay
        )
        every { timeProvider.currentTimeMillis() } returns TEST_TIME
        every { inAppRepository.getShownInApps() } returns mapOf(
            "inapp1" to listOf(TEST_TIME - ONE_HOUR_MS),
            "inapp2" to listOf(TEST_TIME - TWO_HOURS_MS)
        )

        val result = maxInappsPerDayLimitChecker.check()

        assertTrue(result)
    }

    @Test
    fun `check returns false when shown inapps count equals limit`() {
        val maxInappsPerDay = 2

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            maxInappsPerDay = maxInappsPerDay
        )
        every { timeProvider.currentTimeMillis() } returns TEST_TIME
        every { inAppRepository.getShownInApps() } returns mapOf(
            "inapp1" to listOf(TEST_TIME - ONE_HOUR_MS),
            "inapp2" to listOf(TEST_TIME - TWO_HOURS_MS)
        )

        val result = maxInappsPerDayLimitChecker.check()

        assertFalse(result)
    }

    @Test
    fun `check returns false when shown inapps count exceeds limit`() {
        val maxInappsPerDay = 1

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            maxInappsPerDay = maxInappsPerDay
        )
        every { timeProvider.currentTimeMillis() } returns TEST_TIME
        every { inAppRepository.getShownInApps() } returns mapOf(
            "inapp1" to listOf(TEST_TIME - ONE_HOUR_MS),
            "inapp2" to listOf(TEST_TIME - TWO_HOURS_MS)
        )

        val result = maxInappsPerDayLimitChecker.check()

        assertFalse(result)
    }

    @Test
    fun `check ignores inapps shown on previous days`() {
        val maxInappsPerDay = 2

        every { sessionStorageManager.inAppShowLimitsSettings } returns InAppShowLimitsSettings(
            maxInappsPerDay = maxInappsPerDay
        )
        every { timeProvider.currentTimeMillis() } returns TEST_TIME
        every { inAppRepository.getShownInApps() } returns mapOf(
            "inapp1" to listOf(TEST_TIME - ONE_HOUR_MS),
            "inapp2" to listOf(TEST_TIME - ONE_DAY_MS)
        )

        val result = maxInappsPerDayLimitChecker.check()

        assertTrue(result)
    }
}
