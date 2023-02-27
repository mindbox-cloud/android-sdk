package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun Map<String, String>.toUrlQueryString() = LoggingExceptionHandler.runCatching(
    defaultValue = ""
) {
    this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}

internal fun ZonedDateTime.convertToString() = runCatching {
    this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}.getOrElse {
    MindboxLoggerImpl.e("Mindbox", "Error converting date", it)
    ""
}

internal fun Instant.convertToZonedDateTimeAtUTC(): ZonedDateTime {
    return ZonedDateTime.ofInstant(this, ZoneOffset.UTC)
}

internal fun String.convertToZonedDateTime(): ZonedDateTime = runCatching {
    return LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(
        ZoneOffset.UTC
    )
}.getOrElse {
    MindboxLoggerImpl.e("Mindbox", "Error converting date", it)
    LocalDateTime.parse("1970-01-01T00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        .atZone(
            ZoneOffset.UTC
        )
}

internal fun String.convertToZonedDateTimeWithZ(): ZonedDateTime = runCatching {
    return LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
        .atZone(
            ZoneOffset.UTC
        )
}.getOrElse {
    MindboxLoggerImpl.e("Mindbox", "Error converting date", it)
    LocalDateTime.parse("1970-01-01T00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        .atZone(
            ZoneOffset.UTC
        )
}

