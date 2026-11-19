package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.toTimestamp
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DbManagerTest {

    private val now = System.currentTimeMillis().toTimestamp()
    private val olderThanTwoMinutes = event(enqueuedAt = now + -121_000L)
    private val justYoungerThanTwoMinutes = event(enqueuedAt = now + -119_000L)
    private val oneMinuteOld = event(enqueuedAt = now + -60_000L)
    private val createdSecondsBeforeClockMovedBack = event(enqueuedAt = now + 30_000L)
    private val createdHourBeforeClockMovedBack = event(enqueuedAt = now + 3_600_000L)

    @Before
    fun setUp() {
        mockkObject(DbManager)
        every { DbManager.getFilteredEvents() } returns listOf(
            olderThanTwoMinutes,
            justYoungerThanTwoMinutes,
            oneMinuteOld,
            createdSecondsBeforeClockMovedBack,
            createdHourBeforeClockMovedBack,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(DbManager)
    }

    @Test
    fun `background send takes events older than two minutes and events created before the clock was moved back`() {
        assertEquals(
            listOf(olderThanTwoMinutes, createdSecondsBeforeClockMovedBack, createdHourBeforeClockMovedBack),
            DbManager.getFilteredEventsForBackgroundSend(),
        )
    }

    @Test
    fun `background send skips an event added to the queue while the queue is being read`() {
        every { DbManager.getFilteredEvents() } answers {
            Thread.sleep(5L)
            listOf(event(enqueuedAt = System.currentTimeMillis().toTimestamp()))
        }

        assertEquals(emptyList<Event>(), DbManager.getFilteredEventsForBackgroundSend())
    }

    private fun event(enqueuedAt: Timestamp) = Event(
        eventType = EventType.AsyncOperation("Test.Operation"),
        enqueueTimestamp = enqueuedAt.ms,
    )
}
