package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PushDeliveryPauseManagerTest {

    private companion object {
        const val SAMPLES = 2_000
    }

    private val manager = PushDeliveryPauseManager(Random(seed = 656))

    private val lowestJitter = PushDeliveryPauseManager(
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
    )

    private fun samples(pause: () -> Milliseconds): List<Long> = List(SAMPLES) { pause().interval }

    private fun assertSpread(expected: LongRange, values: List<Long>) {
        assertTrue("min ${values.min()} is below ${expected.first}", values.min() >= expected.first)
        assertTrue("max ${values.max()} is above ${expected.last}", values.max() <= expected.last)
        val width = expected.last - expected.first
        assertTrue("values do not reach the lower part of $expected", values.min() < expected.first + width / 10)
        assertTrue("values do not reach the upper part of $expected", values.max() > expected.last - width / 10)
    }

    @Test
    fun `first pause after 429 is a random 150 to 250 seconds`() {
        assertSpread(150_000L..250_000L, samples { manager.nextPause(throttles = 1) })
    }

    @Test
    fun `pause after repeated 429 doubles up to one hour with 25 percent spread`() {
        assertSpread(300_000L..500_000L, samples { manager.nextPause(throttles = 2) })
        assertSpread(600_000L..1_000_000L, samples { manager.nextPause(throttles = 3) })
        assertSpread(2_700_000L..4_500_000L, samples { manager.nextPause(throttles = 9) })
        assertSpread(2_700_000L..4_500_000L, samples { manager.nextPause(throttles = Int.MAX_VALUE) })
    }

    @Test
    fun `pause without a 429 count is the first one`() {
        assertEquals(150_000L, lowestJitter.nextPause(throttles = 0).interval)
    }
}
