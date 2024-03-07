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
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import androidx.annotation.IdRes
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.nio.charset.Charset
import java.util.Queue
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

internal fun ViewGroup.removeChildById(@IdRes viewId: Int) {
    return removeView(findViewById(viewId))
}

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