package cloud.mindbox.mobile_sdk.monitoring.data.checkers

import cloud.mindbox.mobile_sdk.di.monitoringDatabaseName
import io.mockk.MockK
import io.mockk.every
import io.mockk.mockkConstructor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(
    RobolectricTestRunner::class
)
class LogStoringDataCheckerImplTest {


    private val logStoringDataChecker =
        LogStoringDataCheckerImpl(RuntimeEnvironment.getApplication().applicationContext)


    @Test
    fun `database is not exist`() {
        assertThrows(Exception::class.java) {
            logStoringDataChecker.isDatabaseMemorySizeExceeded()
        }
    }

    @Test
    fun `database in memory is not exceeded`() {
        val filePath = "${RuntimeEnvironment.getApplication().applicationContext.filesDir.absolutePath.replace(
            "files",
            "databases"
        )}/$monitoringDatabaseName"
        mockkConstructor(File::class)
        assertFalse(logStoringDataChecker.isDatabaseMemorySizeExceeded())
    }
}