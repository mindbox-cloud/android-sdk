package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.logger.MindboxLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal object Stopwatch : MindboxLog {

    internal const val INIT_SDK = "INIT_SDK"
    internal const val GET_PUSH_TOKENS = "GET_PUSH_TOKENS"

    private val entries: MutableMap<String, Long> by lazy { mutableMapOf() }

    /***
     * Start tracking duration with tag
     */
    fun start(tag: String) {
        entries[tag] = System.nanoTime()
    }

    /***
     * Stop tracking duration from call of [start] with the same tag
     *
     * @return Duration in nanoseconds or null if tag not found
     */
    fun stop(tag: String): Duration? =
        track(tag)?.also {
            entries.remove(tag)
        }

    /***
     * Track duration from call of [start] with the same tag
     *
     * @return Duration in nanoseconds or null if tag not found
     */
    fun track(tag: String): Duration? =
        entries[tag]?.let { start ->
            val now = System.nanoTime()
            (now - start).nanoseconds
        } ?: null.also {
            logW("Stopwatch: $tag: not started")
        }
}
