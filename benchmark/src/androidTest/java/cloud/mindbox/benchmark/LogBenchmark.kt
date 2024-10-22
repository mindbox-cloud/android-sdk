package cloud.mindbox.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class LogBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @UiThreadTest
    fun setUp() {
        Mindbox.initPushServices(context, listOf())
        Mindbox.setLogLevel(Level.NONE)
    }

    @Test
    fun logI() {
        benchmarkRule.measureRepeated {
            Mindbox.writeLog("Log message", Level.INFO)
        }
    }

    @Test
    fun logD() {
        benchmarkRule.measureRepeated {
            Mindbox.writeLog("Log message", Level.DEBUG)
        }
    }
}
