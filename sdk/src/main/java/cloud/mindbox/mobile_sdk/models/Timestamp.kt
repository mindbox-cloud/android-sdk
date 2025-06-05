package cloud.mindbox.mobile_sdk.models

/**
 * Represents a specific point in time as milliseconds since the Unix epoch (January 1, 1970, 00:00:00 UTC)
 */
@JvmInline
internal value class Timestamp(val value: Long) {
    operator fun plus(milliseconds: Long): Timestamp = Timestamp(value + milliseconds)
}
