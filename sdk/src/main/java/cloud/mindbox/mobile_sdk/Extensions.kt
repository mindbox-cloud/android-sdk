package cloud.mindbox.mobile_sdk

import android.util.Log
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.time.LocalDateTime
import java.time.ZoneId
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
    this.withZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}.getOrElse {
    Log.e("Mindbox", "Error converting date", it)
    ""
}


internal fun String.convertToZonedDateTime(): ZonedDateTime = runCatching {
    return LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(
        ZoneOffset.UTC
    )
}.getOrElse {
    Log.e("Mindbox", "Error converting date", it)
    LocalDateTime.parse("1970-01-01T00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        .atZone(
            ZoneId.systemDefault()
        )
}
