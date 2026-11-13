package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPauseManagerTest {

    private var monotonicNow = Milliseconds(5_000L)
    private val manager = DeliveryPauseManager { monotonicNow }
    private val operation = EventType.AsyncOperation("Order")

    @Test
    fun `throttling pauses the operation for 180 seconds`() {
        assertNull(manager.remainingPause(operation))

        val pause = manager.register(operation, AsyncEventSendResult.THROTTLED)

        assertEquals(Milliseconds(180_000L), pause)
        assertEquals(Milliseconds(180_000L), manager.remainingPause(operation))
        advance(Milliseconds(179_999L))
        assertEquals(Milliseconds(1L), manager.remainingPause(operation))
        advance(Milliseconds(1L))
        assertNull(manager.remainingPause(operation))
    }

    @Test
    fun `every next throttling in a row doubles the pause up to an hour`() {
        val pauses = List(7) {
            val pause = checkNotNull(manager.register(operation, AsyncEventSendResult.THROTTLED))
            assertEquals(pause, manager.remainingPause(operation))
            advance(pause)
            assertNull(manager.remainingPause(operation))
            pause
        }

        assertEquals(
            listOf(180L, 360L, 720L, 1440L, 2880L, 3600L, 3600L).map { seconds -> Milliseconds(seconds * 1000L) },
            pauses,
        )
    }

    @Test
    fun `delivered or discarded event after the pause returns the next one to 180 seconds`() {
        listOf(AsyncEventSendResult.DELIVERED, AsyncEventSendResult.DISCARDED).forEach { result ->
            val manager = DeliveryPauseManager { monotonicNow }
            manager.register(operation, AsyncEventSendResult.THROTTLED)
            advance(Milliseconds(180_000L))

            assertNull("$result", manager.register(operation, result))

            assertEquals("$result", Milliseconds(180_000L), manager.register(operation, AsyncEventSendResult.THROTTLED))
        }
    }

    @Test
    fun `operation stays throttled after its pause until an event of it leaves the queue`() {
        val other = EventType.AsyncOperation("Other")
        listOf(AsyncEventSendResult.DELIVERED, AsyncEventSendResult.DISCARDED).forEach { result ->
            val manager = DeliveryPauseManager { monotonicNow }
            assertFalse("$result", manager.wasThrottled(operation))

            manager.register(operation, AsyncEventSendResult.THROTTLED)
            advance(Milliseconds(180_000L))
            manager.register(operation, AsyncEventSendResult.RETRY_LATER)

            assertTrue("$result", manager.wasThrottled(operation))
            assertFalse("$result", manager.wasThrottled(other))
            manager.register(operation, result)
            assertFalse("$result", manager.wasThrottled(operation))
        }
    }

    @Test
    fun `reset forgets that the operation was throttled`() {
        manager.register(operation, AsyncEventSendResult.THROTTLED)

        manager.reset()

        assertFalse(manager.wasThrottled(operation))
    }

    @Test
    fun `answer that arrives during the pause changes neither the pause nor the doubling`() {
        AsyncEventSendResult.values().forEach { result ->
            val manager = DeliveryPauseManager { monotonicNow }
            manager.register(operation, AsyncEventSendResult.THROTTLED)
            advance(Milliseconds(60_000L))

            assertNull("$result", manager.register(operation, result))

            assertEquals("$result", Milliseconds(120_000L), manager.remainingPause(operation))
            advance(Milliseconds(120_000L))
            assertEquals("$result", Milliseconds(360_000L), manager.register(operation, AsyncEventSendResult.THROTTLED))
        }
    }

    @Test
    fun `event kept for a later retry after the pause keeps the doubling`() {
        manager.register(operation, AsyncEventSendResult.THROTTLED)
        advance(Milliseconds(180_000L))

        assertNull(manager.register(operation, AsyncEventSendResult.RETRY_LATER))

        assertNull(manager.remainingPause(operation))
        assertEquals(Milliseconds(360_000L), manager.register(operation, AsyncEventSendResult.THROTTLED))
    }

    @Test
    fun `pause is shared by events of one operation and does not touch other operations`() {
        manager.register(EventType.AsyncOperation("Order"), AsyncEventSendResult.THROTTLED)
        manager.register(EventType.TrackVisit, AsyncEventSendResult.THROTTLED)

        assertNotNull(manager.remainingPause(EventType.AsyncOperation("Order")))
        assertNotNull(manager.remainingPause(EventType.TrackVisit))
        assertNull(manager.remainingPause(EventType.AsyncOperation("Cart")))
        assertNull(manager.remainingPause(EventType.AsyncOperation("TrackVisit")))
        assertNull(manager.remainingPause(EventType.PushClicked))
    }

    @Test
    fun `reset lifts every pause and the doubling`() {
        manager.register(operation, AsyncEventSendResult.THROTTLED)
        manager.register(EventType.TrackVisit, AsyncEventSendResult.THROTTLED)

        manager.reset()

        assertNull(manager.remainingPause(operation))
        assertNull(manager.remainingPause(EventType.TrackVisit))
        assertEquals(Milliseconds(180_000L), manager.register(operation, AsyncEventSendResult.THROTTLED))
    }

    private fun advance(by: Milliseconds) {
        monotonicNow = Milliseconds(monotonicNow.interval + by.interval)
    }
}
