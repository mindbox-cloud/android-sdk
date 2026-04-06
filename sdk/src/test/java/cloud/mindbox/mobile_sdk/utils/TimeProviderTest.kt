package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Timestamp
import io.mockk.every
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeProviderTest {

    private lateinit var timeProvider: SystemTimeProvider

    @Before
    fun setup() {
        timeProvider = spyk(SystemTimeProvider())
    }

    @Test
    fun `elapsedSince returns positive difference when current time is greater`() {
        val inputStartTimeMillis = Timestamp(1000L)
        val expectedElapsed = 500L
        every { timeProvider.currentTimeMillis() } returns 1500L

        val actualElapsed = timeProvider.elapsedSince(inputStartTimeMillis)

        assertEquals(expectedElapsed, actualElapsed.interval)
    }

    @Test
    fun `elapsedSince returns zero when current time equals start time`() {
        val inputStartTimeMillis = Timestamp(1000L)
        val expectedElapsed = 0L
        every { timeProvider.currentTimeMillis() } returns 1000L

        val actualElapsed = timeProvider.elapsedSince(inputStartTimeMillis)

        assertEquals(expectedElapsed, actualElapsed.interval)
    }

    @Test
    fun `elapsedSince returns negative value when current time is less than start time`() {
        val inputStartTimeMillis = Timestamp(2000L)
        val expectedElapsed = -1000L
        every { timeProvider.currentTimeMillis() } returns 1000L

        val actualElapsed = timeProvider.elapsedSince(inputStartTimeMillis)

        assertEquals(expectedElapsed, actualElapsed.interval)
    }

    @Test
    fun `elapsedSince returns correct value when start time is zero`() {
        val inputStartTimeMillis = Timestamp(0L)
        val expectedElapsed = 5000L
        every { timeProvider.currentTimeMillis() } returns 5000L

        val actualElapsed = timeProvider.elapsedSince(inputStartTimeMillis)

        assertEquals(expectedElapsed, actualElapsed.interval)
    }

    @Test
    fun `elapsedSince returns correct value for large timestamps`() {
        val inputStartTimeMillis = Timestamp(1_700_000_000_000L)
        val expectedElapsed = 3500L
        every { timeProvider.currentTimeMillis() } returns 1_700_000_003_500L

        val actualElapsed = timeProvider.elapsedSince(inputStartTimeMillis)

        assertEquals(expectedElapsed, actualElapsed.interval)
    }
}
