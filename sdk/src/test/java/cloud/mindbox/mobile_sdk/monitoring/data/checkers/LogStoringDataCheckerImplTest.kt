package cloud.mindbox.mobile_sdk.monitoring.data.checkers

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

internal class LogStoringDataCheckerImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var file: File

    @InjectMockKs
    private lateinit var logStoringDataChecker: LogStoringDataCheckerImpl

    @Test
    fun `database is not exist`() {
        every { file.exists() } returns false
        assertThrows(Exception::class.java) {
            logStoringDataChecker.isDatabaseMemorySizeExceeded()
        }
    }

    @Test
    fun `database in memory is not exceeded`() {
        every { file.length() } returns LogStoringDataCheckerImpl.MAX_LOG_SIZE.toLong() - 1
        every { file.exists() } returns true
        assertFalse(logStoringDataChecker.isDatabaseMemorySizeExceeded())
    }

    @Test
    fun `database in memory is exceeded`() {
        every { file.length() } returns LogStoringDataCheckerImpl.MAX_LOG_SIZE.toLong()
        every { file.exists() } returns true
        assertTrue(logStoringDataChecker.isDatabaseMemorySizeExceeded())
    }
}
