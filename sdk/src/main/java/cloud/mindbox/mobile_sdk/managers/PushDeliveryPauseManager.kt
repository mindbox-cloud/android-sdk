package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.Milliseconds
import kotlin.random.Random

internal class PushDeliveryPauseManager(
    private val random: Random = Random.Default,
) {

    private companion object {
        const val MAX_PAUSE_DOUBLINGS = 10
        const val JITTER = 0.25
        val FIRST_PAUSE_MIN = Milliseconds(150_000L)
        val FIRST_PAUSE_MAX = Milliseconds(250_000L)
        val PAUSE = Milliseconds(200_000L)
        val MAX_PAUSE = Milliseconds(3_600_000L)
    }

    fun nextPause(throttles: Int): Milliseconds =
        if (throttles <= 1) {
            Milliseconds(random.nextLong(FIRST_PAUSE_MIN.interval, FIRST_PAUSE_MAX.interval + 1))
        } else {
            jittered(doubled(PAUSE, doublings = throttles - 1))
        }

    private fun doubled(pause: Milliseconds, doublings: Int): Milliseconds {
        val shift = doublings.coerceIn(0, MAX_PAUSE_DOUBLINGS)
        return Milliseconds(minOf(pause.interval shl shift, MAX_PAUSE.interval))
    }

    private fun jittered(pause: Milliseconds): Milliseconds {
        val spread = Milliseconds((pause.interval * JITTER).toLong())
        return Milliseconds(pause.interval + random.nextLong(-spread.interval, spread.interval + 1))
    }
}
