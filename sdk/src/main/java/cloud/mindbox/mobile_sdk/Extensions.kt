package cloud.mindbox.mobile_sdk

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.view.View
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import kotlin.math.roundToInt


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

internal fun String?.equalsAny(vararg values: String): Boolean = values.any { this == it }

internal inline fun <reified T : Enum<T>> String?.enumValue(default: T? = null): T {
    return this?.let {
        enumValues<T>().firstOrNull { value ->
            value.name
                .replace("_", "")
                .equals(
                    this.replace("_", "").trim(),
                    ignoreCase = true
                )
        }
    } ?: default ?: throw IllegalArgumentException("Value for $this could not be found")
}

internal fun Double?.isInRange(start: Double, end: Double): Boolean {
    if (this == null) return false
    return (this > start) && (this < end)
}

internal fun Context.isMainProcess(processName: String?): Boolean {
    val mainProcessName = getString(R.string.mindbox_android_process).ifBlank { packageName }
    return processName?.equalsAny(
        mainProcessName,
        "$packageName:$mainProcessName",
        "$packageName$mainProcessName",
    ) ?: false
}

internal fun Context.getCurrentProcessName(): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return Application.getProcessName()
    }

    val mypid = Process.myPid()
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val processes = manager.runningAppProcesses

    return processes.firstOrNull { info -> info.pid == mypid }?.processName
}

internal fun View.setSingleClickListener(listener: View.OnClickListener) {
    setOnClickListener {
        setOnClickListener(null)
        listener.onClick(it)
    }
}

val Double.px: Double
    get() = (this * Resources.getSystem().displayMetrics.density)

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()
val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()

