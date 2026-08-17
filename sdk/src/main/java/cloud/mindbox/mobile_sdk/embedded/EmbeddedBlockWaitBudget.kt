package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.SystemClock
import cloud.mindbox.mobile_sdk.models.Milliseconds

internal class EmbeddedBlockWaitBudget(
    private val duration: Milliseconds,
    private val mainHandler: Handler,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val onExpire: () -> Unit,
) {

    private var consumedMs = 0L
    private var resumedAt: Long? = null

    private val expireRunnable = Runnable {
        resumedAt = null
        consumedMs = duration.interval
        onExpire()
    }

    private val remainingMs: Long
        get() = (duration.interval - consumedMs).coerceAtLeast(0L)

    fun armIfNeeded() {
        if (resumedAt != null) return
        resumedAt = now()
        mainHandler.postDelayed(expireRunnable, remainingMs)
    }

    fun pause() {
        val startedAt = resumedAt ?: return
        consumedMs += (now() - startedAt).coerceAtLeast(0L)
        resumedAt = null
        mainHandler.removeCallbacks(expireRunnable)
    }

    fun reset() {
        pause()
        consumedMs = 0L
    }
}
