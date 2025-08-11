package cloud.mindbox.mobile_sdk.models

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a time span in the format "[-]d.hh:mm:ss[.fraction]"
 * Examples:
 * - "00:30:00" - 30 minutes
 * - "1.00:00:00" - 1 day
 * - "0.00:00:10" - 10 seconds
 * - "-1.00:00:00" - negative 1 day
 */
@JvmInline
internal value class TimeSpan private constructor(val value: String) {
    fun toMillis(): Long {
        val matchResult = TIME_SPAN_REGEX.matchEntire(value) ?: throw IllegalArgumentException("Invalid time span format: $value")
        val (sign, days, hours, minutes, seconds, fraction) = matchResult.destructured
        val daysCorrected = if (days.isBlank()) "0" else days.dropLast(1)

        val duration = daysCorrected.toLong().days +
            hours.toLong().hours +
            minutes.toLong().minutes +
            (seconds + fraction).toDouble().seconds

        return if (sign == "-") duration.inWholeMilliseconds * -1 else duration.inWholeMilliseconds
    }

    companion object {
        private val TIME_SPAN_REGEX = """(-)?(\d+\.)?([01]?\d|2[0-3]):([0-5]?\d):([0-5]?\d)(\.\d{1,7})?""".toRegex()

        private fun isValidTimeSpan(value: String): Boolean =
            TIME_SPAN_REGEX.matches(value)

        fun fromStringOrNull(value: String?): TimeSpan? =
            value
                ?.takeIf { isValidTimeSpan(value) }
                ?.let { TimeSpan(value) }
    }
}

internal fun TimeSpan?.toMilliseconds(): Milliseconds? = this?.toMillis().toMilliseconds()
