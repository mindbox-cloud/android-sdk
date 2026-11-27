package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
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

    @Test
    fun `push delivery rows are stored, updated and removed through the manager`() = withDatabase { database ->
        val id = checkNotNull(DbManager.addPushDelivery(PushDelivery(url = "https://mbx.ru/d", receivedAt = 5L, provider = "FCM")))
        val other = checkNotNull(DbManager.addPushDelivery(PushDelivery(url = "https://mbx.ru/2", receivedAt = 6L)))

        val updated = PushDelivery(
            id = id,
            url = "https://mbx.ru/d",
            receivedAt = 5L,
            attempts = 2,
            provider = "FCM",
            isInQueue = true,
        )
        assertEquals(1, DbManager.updatePushDelivery(updated))
        assertEquals(updated, DbManager.getPushDelivery(id))
        assertEquals(listOf(id), DbManager.getQueuedPushDeliveries().map { it.id })
        assertEquals(listOf(id, other), database.pushDeliveryIds())
        assertEquals(listOf(other), DbManager.getPushDeliveryIdsOutOfQueue())

        assertTrue(DbManager.removePushDelivery(id))
        assertNull(DbManager.getPushDelivery(id))
        assertEquals(0, DbManager.updatePushDelivery(updated.copy(attempts = 3)))
        assertTrue(DbManager.getQueuedPushDeliveries().isEmpty())

        DbManager.removeAllPushDeliveries()
        assertTrue(database.pushDeliveryIds().isEmpty())
    }

    @Test
    fun `queued push deliveries come with fewer attempts first, then oldest first`() = withDatabase {
        val ids = listOf(4, 1, 1, 0).map { attempts ->
            checkNotNull(DbManager.addPushDelivery(PushDelivery(url = "https://mbx.ru/d", receivedAt = 5L, attempts = attempts, isInQueue = true)))
        }

        assertEquals(listOf(ids[3], ids[1], ids[2], ids[0]), DbManager.getQueuedPushDeliveries().map { it.id })
    }

    private fun withDatabase(block: (MindboxDatabase) -> Unit) {
        unmockkObject(DbManager)
        val database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MindboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val field = DbManager::class.java.getDeclaredField("mindboxDb").apply { isAccessible = true }
        val previous = field.get(DbManager)
        field.set(DbManager, database)
        try {
            block(database)
        } finally {
            field.set(DbManager, previous)
            database.close()
        }
    }

    private fun event(enqueuedAt: Timestamp) = Event(
        eventType = EventType.AsyncOperation("Test.Operation"),
        enqueueTimestamp = enqueuedAt.ms,
    )

    private fun MindboxDatabase.pushDeliveryIds(): List<Long> =
        query("SELECT id FROM mindbox_push_deliveries_table ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
        }
}
