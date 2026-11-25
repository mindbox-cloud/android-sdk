package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
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
    fun `push delivery rows are stored, updated and removed through the manager`() {
        unmockkObject(DbManager)
        val database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MindboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val field = DbManager::class.java.getDeclaredField("mindboxDb").apply { isAccessible = true }
        val previous = field.get(DbManager)
        field.set(DbManager, database)
        try {
            val id = checkNotNull(DbManager.addPushDelivery(PushDelivery(url = "https://mbx.ru/d", receivedAt = 5L, isHighPriority = true, provider = "FCM")))
            val other = checkNotNull(DbManager.addPushDelivery(PushDelivery(url = "https://mbx.ru/2", receivedAt = 6L)))

            val updated = PushDelivery(
                id = id,
                url = "https://mbx.ru/d",
                receivedAt = 5L,
                attempts = 2,
                throttles = 1,
                isHighPriority = true,
                provider = "FCM",
                nextAttemptAt = 70L,
            )
            assertEquals(1, DbManager.updatePushDelivery(updated))
            assertEquals(updated, DbManager.getPushDelivery(id))
            assertEquals(listOf(id), DbManager.getDuePushDeliveries(Timestamp(70L), Milliseconds(100L), limit = 5).map { it.id })
            assertTrue(DbManager.getDuePushDeliveries(Timestamp(69L), Milliseconds(100L), limit = 5).isEmpty())
            assertEquals(listOf(id), DbManager.getDuePushDeliveries(Timestamp(10L), Milliseconds(59L), limit = 5).map { it.id })
            assertEquals(Timestamp(70L), DbManager.getEarliestPushDeliveryAttemptTime())
            assertEquals(listOf(id, other), database.pushDeliveryIds())
            assertEquals(listOf(other), DbManager.getPushDeliveryIdsOutOfQueue())

            assertTrue(DbManager.removePushDelivery(id))
            assertNull(DbManager.getPushDelivery(id))
            assertEquals(0, DbManager.updatePushDelivery(updated.copy(attempts = 3)))
            assertNull(DbManager.getEarliestPushDeliveryAttemptTime())

            DbManager.removeAllPushDeliveries()
            assertTrue(database.pushDeliveryIds().isEmpty())
        } finally {
            field.set(DbManager, previous)
            database.close()
        }
    }

    private fun event(enqueuedAt: Timestamp) = Event(
        eventType = EventType.AsyncOperation("Test.Operation"),
        enqueueTimestamp = enqueuedAt.ms,
    )

    @Test
    fun `due push deliveries come freshest first and a pause moves only the rows that are not fresher and not later`() {
        unmockkObject(DbManager)
        val database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MindboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val field = DbManager::class.java.getDeclaredField("mindboxDb").apply { isAccessible = true }
        val previous = field.get(DbManager)
        field.set(DbManager, database)
        try {
            val dao = database.pushDeliveriesDao()

            fun add(attempts: Int, nextAttemptAt: Long?): Long = dao.insert(
                PushDelivery(url = "https://mbx.ru/d", receivedAt = 1L, attempts = attempts, nextAttemptAt = nextAttemptAt)
            )

            val stale = add(attempts = 9, nextAttemptAt = 10L)
            val fresh = add(attempts = 3, nextAttemptAt = 60L)
            val middle = add(attempts = 5, nextAttemptAt = 50L)
            val middleEarlier = add(attempts = 5, nextAttemptAt = 40L)
            val notDue = add(attempts = 6, nextAttemptAt = 150L)
            val later = add(attempts = 7, nextAttemptAt = 900L)
            val afterClockChange = add(attempts = 8, nextAttemptAt = 5_000L)
            val ownedByTask = add(attempts = 9, nextAttemptAt = null)

            assertEquals(
                listOf(fresh, middleEarlier, middle, afterClockChange, stale),
                DbManager.getDuePushDeliveries(Timestamp(100L), Milliseconds(1_000L), limit = 10).map { it.id },
            )

            val failed = add(attempts = 6, nextAttemptAt = 300L)
            val postponed = DbManager.postponePushDeliveries(
                minAttempts = 5,
                attempts = 6,
                throttles = 2,
                until = Timestamp(300L),
                now = Timestamp(100L),
                longestPause = Milliseconds(1_000L),
                ownPauseAttempts = 5,
                ownPauseReceivedAfter = Timestamp(55L),
            )

            assertEquals(5, postponed)
            listOf(stale, middle, middleEarlier, notDue, afterClockChange).forEach { id ->
                assertEquals("row $id", 299L, DbManager.getPushDelivery(id)?.nextAttemptAt)
                assertEquals("row $id", 2, DbManager.getPushDelivery(id)?.throttles)
            }
            assertEquals(300L, DbManager.getPushDelivery(failed)?.nextAttemptAt)
            assertEquals(0, DbManager.getPushDelivery(failed)?.throttles)
            val levelAfterPause = DbManager.getDuePushDeliveries(Timestamp(300L), Milliseconds(1_000L), limit = 10).map { it.id }
            assertEquals(setOf(middle, middleEarlier, notDue), levelAfterPause.drop(1).take(3).toSet())
            assertEquals(failed, levelAfterPause[4])

            val firstOfTheLevel = List(60) {
                DbManager.getDuePushDeliveries(Timestamp(300L), Milliseconds(1_000L), limit = 10)[1].id
            }.toSet()
            assertTrue("first of the level was always $firstOfTheLevel", firstOfTheLevel.size > 1)

            DbManager.postponePushDeliveries(
                minAttempts = 9,
                attempts = 9,
                throttles = 2,
                until = Timestamp(400L),
                now = Timestamp(300L),
                longestPause = Milliseconds(1_000L),
                ownPauseAttempts = 5,
                ownPauseReceivedAfter = Timestamp(255L),
            )
            assertEquals(399L, DbManager.getPushDelivery(stale)?.nextAttemptAt)
            assertEquals(2, DbManager.getPushDelivery(stale)?.throttles)
            assertEquals(
                listOf(9, 6, 6, 6, 8),
                listOf(stale, middle, middleEarlier, notDue, afterClockChange).map { DbManager.getPushDelivery(it)?.attempts },
            )
            assertEquals(60L, DbManager.getPushDelivery(fresh)?.nextAttemptAt)
            assertEquals(3, DbManager.getPushDelivery(fresh)?.attempts)
            assertEquals(0, DbManager.getPushDelivery(fresh)?.throttles)
            assertEquals(7, DbManager.getPushDelivery(later)?.attempts)
            assertEquals(900L, DbManager.getPushDelivery(later)?.nextAttemptAt)
            assertNull(DbManager.getPushDelivery(ownedByTask)?.nextAttemptAt)
        } finally {
            field.set(DbManager, previous)
            database.close()
        }
    }

    @Test
    fun `pause of the queue leaves alone the rows that are younger than 45 seconds and have fewer than five attempts`() {
        unmockkObject(DbManager)
        val database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MindboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val field = DbManager::class.java.getDeclaredField("mindboxDb").apply { isAccessible = true }
        val previous = field.get(DbManager)
        field.set(DbManager, database)
        try {
            val dao = database.pushDeliveriesDao()

            fun add(attempts: Int, receivedAt: Long): Long = dao.insert(
                PushDelivery(url = "https://mbx.ru/d", receivedAt = receivedAt, attempts = attempts, nextAttemptAt = 100_010L)
            )

            val young = add(attempts = 4, receivedAt = 55_001L)
            val receivedAfterNow = add(attempts = 3, receivedAt = 500_000L)
            val exactly45SecondsOld = add(attempts = 4, receivedAt = 55_000L)
            val youngWithFiveAttempts = add(attempts = 5, receivedAt = 99_000L)
            val old = add(attempts = 3, receivedAt = 1L)

            val postponed = DbManager.postponePushDeliveries(
                minAttempts = 2,
                attempts = 3,
                throttles = 1,
                until = Timestamp(300_000L),
                now = Timestamp(100_000L),
                longestPause = Milliseconds(4_500_000L),
                ownPauseAttempts = 5,
                ownPauseReceivedAfter = Timestamp(55_000L),
            )

            assertEquals(3, postponed)
            listOf(young, receivedAfterNow).forEach { id ->
                assertEquals("row $id", 100_010L, DbManager.getPushDelivery(id)?.nextAttemptAt)
                assertEquals("row $id", 0, DbManager.getPushDelivery(id)?.throttles)
            }
            listOf(exactly45SecondsOld, youngWithFiveAttempts, old).forEach { id ->
                assertEquals("row $id", 299_999L, DbManager.getPushDelivery(id)?.nextAttemptAt)
                assertEquals("row $id", 1, DbManager.getPushDelivery(id)?.throttles)
            }
        } finally {
            field.set(DbManager, previous)
            database.close()
        }
    }

    private fun MindboxDatabase.pushDeliveryIds(): List<Long> =
        query("SELECT id FROM mindbox_push_deliveries_table ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
        }
}
