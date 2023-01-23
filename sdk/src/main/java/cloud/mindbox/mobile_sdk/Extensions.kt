package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun Map<String, String>.toUrlQueryString() = LoggingExceptionHandler.runCatching(
    defaultValue = ""
) {
    this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}

internal fun String.convertToLongDateSeconds(): Long {
    return LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:SS")).atZone(
        ZoneId.systemDefault()
    ).toEpochSecond() * 1000
}

internal fun Long.convertToStringDate(): String {
    return ZonedDateTime.ofInstant(Instant.ofEpochSecond(this / 1000), ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:SS"))
}
