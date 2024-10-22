package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class LogTests {

    @Before
    fun init() {
        setDatabaseTestMode(true)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Mindbox.initPushServices(appContext, listOf())
    }

    @After
    fun clear() {
        DbManager.removeAllEventsFromQueue()
        clearPreferences()
    }

    private val logs = List<MonitoringEntity>(1000) {
        MonitoringEntity(
            time = "2021-10-10T10:10:10Z",
            log = "INFO",
        )
    }

    @Test
    fun insertLog1000() = runTest {
        println("start" + System.currentTimeMillis())
        logs.forEach { MindboxDI.appModule.monitoringDao.insertLog(it) }
        println("end" + System.currentTimeMillis())
    }

    @Test
    fun insertLogs1000() = runTest {
        println("start" + System.currentTimeMillis())
        MindboxDI.appModule.monitoringDao.insertLogs(logs)
        println("end" + System.currentTimeMillis())
    }
}
