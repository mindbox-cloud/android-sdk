package cloud.mindbox.mobile_sdk.utils

import android.util.Log
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import kotlin.time.Duration.Companion.nanoseconds

internal object Stopwatch {

    private val entries: MutableMap<String, Long> by lazy { mutableMapOf() }

    fun start(tag: String) {
        entries[tag] = System.nanoTime()
        Log.d(MindboxLoggerImpl.TAG, "Stopwatch: Start $tag")
    }

    fun stop(tag: String) {
        val start = entries[tag]
        if (start == null) {
            Log.d(MindboxLoggerImpl.TAG, "Stopwatch: $tag: not started")
            return
        }
        val duration = (System.nanoTime() - start).nanoseconds
        entries.remove(tag)
        Log.d(MindboxLoggerImpl.TAG, "Stopwatch: Stop $tag: $duration")
    }
}
