package cloud.mindbox.mobile_sdk.monitoring.data.checkers

import android.content.Context
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LogStoringDataCheckerImplTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var context: Context

    @InjectMockKs
    private lateinit var logStoringDataChecker: LogStoringDataCheckerImpl

    @Test
    fun `database memory is not exceeded returns false`() {
        /*every {
            context.filesDir.absolutePath.replace(
                "files",
                "databases"
            )
        } returns ""*/

        assertFalse(logStoringDataChecker.isDatabaseMemorySizeExceeded())
    }
}