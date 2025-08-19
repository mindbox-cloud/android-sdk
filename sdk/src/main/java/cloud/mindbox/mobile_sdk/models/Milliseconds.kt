package cloud.mindbox.mobile_sdk.models

/**
 * Represents time duration in milliseconds
 */
@JvmInline
internal value class Milliseconds(val interval: Long)

internal fun Long?.toMilliseconds(): Milliseconds? = this?.let { Milliseconds(it) }
