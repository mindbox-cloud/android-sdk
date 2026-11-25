package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MindboxDatabaseMigrationTest {

    private companion object {
        const val DATABASE_NAME = "mindbox_db"
        const val VERSION_4 = 4
        const val VERSION_4_IDENTITY_HASH = "820b0e84b0c763e84ecba794455b957c"
        val VERSION_4_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `mindbox_configuration_table` (`configurationId` INTEGER NOT NULL, " +
                "`previousInstallationId` TEXT NOT NULL, `previousDeviceUUID` TEXT NOT NULL, `endpointId` TEXT NOT NULL, " +
                "`domain` TEXT NOT NULL, `packageName` TEXT NOT NULL, `versionName` TEXT NOT NULL, `versionCode` TEXT NOT NULL, " +
                "`subscribeCustomerIfCreated` INTEGER NOT NULL, `shouldCreateCustomer` INTEGER NOT NULL, `operationsDomain` TEXT, " +
                "`shouldIncludeVersionCode` INTEGER NOT NULL, PRIMARY KEY(`configurationId`))",
            "CREATE TABLE IF NOT EXISTS `mindbox_events_table` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventType` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `enqueueTimestamp` INTEGER NOT NULL, " +
                "`additionalFields` TEXT, `body` TEXT)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$VERSION_4_IDENTITY_HASH')",
        )
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val executor = Executors.newSingleThreadExecutor()
    private var database: MindboxDatabase? = null

    @Before
    fun setUp() {
        mockLogger()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        executor.shutdownNow()
        unmockkAll()
    }

    private fun createVersion4Database(fill: (SQLiteDatabase) -> Unit = {}) {
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            VERSION_4_SCHEMA.forEach(db::execSQL)
            fill(db)
            db.version = VERSION_4
        }
    }

    private fun MindboxDatabase.pushDeliveryIds(): List<Long> =
        query("SELECT id FROM mindbox_push_deliveries_table ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
        }

    private fun <T> onWorkerThread(block: (MindboxDatabase) -> T): T {
        val opened = database ?: MindboxDatabase.getInstance(context).also { database = it }
        return executor.submit(Callable { block(opened) }).get()
    }

    @Test
    fun `database of the previous SDK version opens and gets the push deliveries table`() {
        createVersion4Database()

        val id = onWorkerThread { db ->
            db.pushDeliveriesDao().insert(
                PushDelivery(url = "https://mbx.ru/d/key?sig=1", receivedAt = 1_000L, isHighPriority = true, provider = "FCM")
            )
        }

        assertEquals(
            PushDelivery(id = id, url = "https://mbx.ru/d/key?sig=1", receivedAt = 1_000L, isHighPriority = true, provider = "FCM"),
            onWorkerThread { db -> db.pushDeliveriesDao().get(id) },
        )
    }

    @Test
    fun `events and configuration of the previous SDK version survive the migration`() {
        createVersion4Database { db ->
            db.execSQL(
                "INSERT INTO mindbox_events_table (eventType, transactionId, enqueueTimestamp, additionalFields, body) " +
                    "VALUES ('3;{\"operation\":\"MobilePush.TrackClick\",\"endpoint\":\"/v3/operations/async\"}', 'tx-1', 5, NULL, '{}')"
            )
            db.execSQL(
                "INSERT INTO mindbox_configuration_table VALUES (0, '', '', 'endpoint', 'api.mindbox.ru', 'app', '1.0', '1', 1, 1, NULL, 1)"
            )
        }

        val events: List<Event> = onWorkerThread { db -> db.eventsDao().getAll() }

        assertEquals(listOf("tx-1"), events.map { it.transactionId })
        assertEquals(EventType.PushClicked, events.single().eventType)
        assertEquals("endpoint", onWorkerThread { db -> db.configurationDao().get().endpointId })
        assertTrue(onWorkerThread { db -> db.pushDeliveryIds() }.isEmpty())
    }

    @Test
    fun `rows keep their own counters and are removed one by one or all at once`() {
        createVersion4Database()
        val first = onWorkerThread { db -> db.pushDeliveriesDao().insert(PushDelivery(url = "https://mbx.ru/1", receivedAt = 1L)) }
        val second = onWorkerThread { db -> db.pushDeliveriesDao().insert(PushDelivery(url = "https://mbx.ru/2", receivedAt = 2L)) }

        assertEquals(1, onWorkerThread { db -> db.pushDeliveriesDao().update(id = first, attempts = 4, throttles = 2, nextAttemptAt = 900L) })
        assertEquals(
            PushDelivery(id = first, url = "https://mbx.ru/1", receivedAt = 1L, attempts = 4, throttles = 2, nextAttemptAt = 900L),
            onWorkerThread { db -> db.pushDeliveriesDao().get(first) },
        )
        assertEquals(PushDelivery(id = second, url = "https://mbx.ru/2", receivedAt = 2L), onWorkerThread { db -> db.pushDeliveriesDao().get(second) })

        onWorkerThread { db -> db.pushDeliveriesDao().delete(first) }
        assertNull(onWorkerThread { db -> db.pushDeliveriesDao().get(first) })
        assertEquals(0, onWorkerThread { db -> db.pushDeliveriesDao().update(id = first, attempts = 5, throttles = 0, nextAttemptAt = null) })
        assertEquals(listOf(second), onWorkerThread { db -> db.pushDeliveryIds() })

        onWorkerThread { db -> db.pushDeliveriesDao().deleteAll() }
        assertTrue(onWorkerThread { db -> db.pushDeliveryIds() }.isEmpty())
    }

    @Test
    fun `queue reads only rows that were handed to it and whose time has come, earliest first at equal counters`() {
        createVersion4Database()
        val dao = onWorkerThread { db -> db.pushDeliveriesDao() }
        val ownedByItsTask = onWorkerThread { dao.insert(PushDelivery(url = "https://mbx.ru/1", receivedAt = 1L)) }
        val late = onWorkerThread { dao.insert(PushDelivery(url = "https://mbx.ru/2", receivedAt = 2L, nextAttemptAt = 500L)) }
        val early = onWorkerThread { dao.insert(PushDelivery(url = "https://mbx.ru/3", receivedAt = 3L, nextAttemptAt = 300L)) }
        val future = onWorkerThread { dao.insert(PushDelivery(url = "https://mbx.ru/4", receivedAt = 4L, nextAttemptAt = 900L)) }

        assertEquals(listOf(early, late), onWorkerThread { dao.getDue(now = 500L, latest = 900L, limit = 10) }.map { it.id })
        assertEquals(listOf(early), onWorkerThread { dao.getDue(now = 500L, latest = 900L, limit = 1) }.map { it.id })
        assertTrue(onWorkerThread { dao.getDue(now = 299L, latest = 900L, limit = 10) }.isEmpty())
        assertEquals(listOf(future), onWorkerThread { dao.getDue(now = 299L, latest = 899L, limit = 10) }.map { it.id })
        assertEquals(300L, onWorkerThread { dao.getEarliestAttemptTime() })
        assertEquals(listOf(ownedByItsTask), onWorkerThread { dao.getIdsOutOfQueue() })

        onWorkerThread { listOf(late, early, future).forEach(dao::delete) }
        assertNull(onWorkerThread { dao.getEarliestAttemptTime() })
        assertEquals(listOf(ownedByItsTask), onWorkerThread { db -> db.pushDeliveryIds() })
    }
}
