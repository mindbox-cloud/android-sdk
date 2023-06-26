package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Process
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

internal fun View.hideKeyboard(): View? {
    val imm = this.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    if (isKeyboardOpen()) {
        imm.hideSoftInputFromWindow(this.windowToken, 0)
        return this
    }
    return null
}

internal fun View.showKeyboard(): View {
    val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    this.requestFocus()
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    return this
}

internal fun Context.convertDpToPx(dp: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        this.resources.displayMetrics
    )
}

internal fun View.isKeyboardOpen(): Boolean {
    val visibleBounds = Rect()
    this.rootView.getWindowVisibleDisplayFrame(visibleBounds)
    val heightDiff = rootView.height - visibleBounds.height()
    val marginOfError = this.context.convertDpToPx(100F).roundToInt()
    return heightDiff > marginOfError
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