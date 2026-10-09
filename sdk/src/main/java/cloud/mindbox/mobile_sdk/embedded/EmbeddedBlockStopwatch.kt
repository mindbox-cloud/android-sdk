package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.Milliseconds

internal class EmbeddedBlockStopwatch(
    private val now: () -> Milliseconds,
) {

    private var settled = Milliseconds(0L)
    private var runningSince: Milliseconds? = null

    val elapsed: Milliseconds
        get() = Milliseconds(settled.interval + (runningSince?.let(::spanSince)?.interval ?: 0L))

    fun resume() {
        if (runningSince == null) runningSince = now()
    }

    fun pause() {
        val since = runningSince ?: return
        settled = Milliseconds(settled.interval + spanSince(since).interval)
        runningSince = null
    }

    private fun spanSince(since: Milliseconds): Milliseconds =
        Milliseconds((now().interval - since.interval).coerceAtLeast(0L))
}
