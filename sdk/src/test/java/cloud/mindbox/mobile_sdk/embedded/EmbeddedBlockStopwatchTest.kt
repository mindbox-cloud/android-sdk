package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedBlockStopwatchTest {

    private var clock = Milliseconds(1_000L)
    private val stopwatch = EmbeddedBlockStopwatch(now = { clock })

    private fun advance(by: Milliseconds) {
        clock = Milliseconds(clock.interval + by.interval)
    }

    @Test
    fun `a repeated resume keeps the running stretch from its first start`() {
        stopwatch.resume()
        advance(Milliseconds(400L))
        stopwatch.resume()
        advance(Milliseconds(100L))

        assertEquals(Milliseconds(500L), stopwatch.elapsed)
    }

    @Test
    fun `a repeated pause counts the stretch once`() {
        stopwatch.resume()
        advance(Milliseconds(400L))
        stopwatch.pause()
        advance(Milliseconds(5_000L))
        stopwatch.pause()

        assertEquals(Milliseconds(400L), stopwatch.elapsed)
    }
}
