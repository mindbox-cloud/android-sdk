package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.res.Resources
import android.os.Build
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.annotation.IdRes
import cloud.mindbox.mobile_sdk.Mindbox.logE
import cloud.mindbox.mobile_sdk.Mindbox.logW
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTime
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.SessionDelay
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.nio.charset.Charset
import java.util.Queue
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal fun SessionDelay(): SessionDelay {
    return Frequency.Delay.TimeDelay(0, InAppTime.SECONDS)
}

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

internal fun String?.equalsAny(vararg values: String, ignoreCase: Boolean = false): Boolean = values.any { this?.equals(it, ignoreCase) == true }

internal inline fun <reified T : Enum<T>> String?.enumValue(default: T? = null): T = this?.let {
    enumValues<T>().firstOrNull { value ->
        value.name
            .replace("_", "")
            .equals(
                this.replace("_", "").trim(),
                ignoreCase = true
            )
    }
} ?: default ?: throw IllegalArgumentException("Value for $this could not be found")

internal fun Double?.isInRange(start: Double, end: Double): Boolean {
    if (this == null) return false
    return this in start..end
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
internal typealias SnackbarPosition = InAppType.Snackbar.Position.Gravity.VerticalGravity

internal fun InAppType.Snackbar.isTop(): Boolean {
    return position.gravity.vertical == SnackbarPosition.TOP
}

internal val Double.px: Double
    get() = (this * Resources.getSystem().displayMetrics.density)

internal val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()
internal val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()

internal fun Animation.setOnAnimationEnd(runnable: Runnable) {
    setAnimationListener(object : AnimationListener {
        override fun onAnimationStart(animation: Animation?) {
        }

        override fun onAnimationEnd(animation: Animation?) {
            runnable.run()
        }

        override fun onAnimationRepeat(animation: Animation?) {
        }
    })
}

internal fun ViewGroup.removeChildById(
    @IdRes viewId: Int
) = removeView(findViewById(viewId))

internal val Activity?.root: ViewGroup?
    get() = this?.window?.decorView?.rootView as ViewGroup?

internal fun Activity.postDelayedAnimation(action: Runnable) {
    val durationAnimationDefault = 400L
    val duration = try {
        resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
    } catch (_: Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.transitionBackgroundFadeDuration
        } else {
            durationAnimationDefault
        }
    }
    this.root?.postDelayed(action, duration)
}

internal inline fun <T> Queue<T>.addUnique(
    item: T,
    predicate: (T) -> Boolean = { it == item }
): Boolean {
    if (any(predicate)) return false
    add(item)
    return true
}

internal fun PackageManager.getPackageInfoCompat(context: Context, flags: Int): PackageInfo {
    return if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
        getPackageInfo(context.packageName, PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(context.packageName, flags)
    }
}

internal fun VolleyError.getErrorResponseBodyData(): String {
    return this.networkResponse?.data
        ?.takeIf { it.isNotEmpty() }
        ?.toString(
            Charset.forName(
                HttpHeaderParser.parseCharset(
                    this.networkResponse?.headers ?: emptyMap()
                )
            )
        )
        ?: ""
}

internal fun verifyThreadExecution(methodName: String, shouldBeMainThread: Boolean = true) {
    val isMainThread = Looper.myLooper() == Looper.getMainLooper()
    when {
        shouldBeMainThread && !isMainThread -> logE("Method $methodName must be called on the main thread")
        !shouldBeMainThread && isMainThread -> logW("Method $methodName should not be called on the main thread")
    }
}

internal fun String.parseTimeSpanToMillis(): Long {
    val regex = """(-)?(\d+\.)?([01]?\d|2[0-3]):([0-5]?\d):([0-5]?\d)(\.\d{1,7})?""".toRegex()
    val matchResult = regex.matchEntire(this)
        ?: throw IllegalArgumentException("Invalid timeSpan format")
    val (sign, days, hours, minutes, seconds, fraction) = matchResult.destructured
    val daysCorrected = if (days.isBlank()) "0" else days.dropLast(1)

    val duration = daysCorrected.toLong().days +
        hours.toLong().hours +
        minutes.toLong().minutes +
        (seconds + fraction).toDouble().seconds

    return if (sign == "-") duration.inWholeMilliseconds * -1 else duration.inWholeMilliseconds
}

internal fun String.isUuid(): Boolean {
    return loggingRunCatching(false) {
        UUID.fromString(this)
        true
    }
}

internal inline fun <reified T> Gson.fromJson(json: JsonElement?): Result<T> = runCatching {
    fromJson(json, object : TypeToken<T>() {}.type)
}

internal fun JsonObject.getOrNull(memberName: String?): JsonElement? = runCatching {
    this.get(memberName)
}.getOrNull()

internal inline fun <T> Result<T>.getOrNull(runIfNull: (Throwable) -> Unit): T? = getOrElse {
    runIfNull(it)
    null
}
