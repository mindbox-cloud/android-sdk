package cloud.mindbox.mobile_sdk.logger

import android.util.Log
import cloud.mindbox.mobile_sdk.convertToZonedDateTimeAtUTC
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import com.android.volley.VolleyLog
import kotlinx.coroutines.*
import org.threeten.bp.Instant

/**
 * An interface for internal sdk work only. Do not implement or use it.
 * */
public interface MindboxLogger {

    public fun i(parent: Any, message: String)

    public fun d(parent: Any, message: String)

    public fun e(parent: Any, message: String)

    public fun e(parent: Any, message: String, exception: Throwable)

    public fun w(parent: Any, message: String)

    public fun w(parent: Any, message: String, exception: Throwable)
}

internal object MindboxLoggerImpl : MindboxLogger {

    const val TAG = "Mindbox"

    private val DEFAULT_LOG_LEVEL = Level.WARN

    private val monitoringRepository: MonitoringRepository by mindboxInject { monitoringRepository }

    val monitoringScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Mindbox monitoring caught unhandled error", throwable)
        })

    init {
        runCatching {
            VolleyLog.DEBUG = false
        }
    }

    @Volatile
    internal var level: Level = DEFAULT_LOG_LEVEL

    /**
     * Returns [Level.DEBUG] if `adb shell setprop log.tag.Mindbox DEBUG` (or VERBOSE) is active,
     * [Level.NONE] otherwise. Any other setprop value is treated as "not set".
     *
     * When [Level.DEBUG] is returned it overrides the programmatic [level], enabling all log
     * output regardless of what [level] is currently configured to.
     *
     * `Log.isLoggable(TAG, Log.DEBUG)` returns `true` for both VERBOSE and DEBUG setprop values
     * because VERBOSE has a lower priority than DEBUG in Android's priority scale.
     * The property is consulted when execution reaches the setprop override check, so changes
     * take effect immediately without app restart for calls that evaluate this branch.
     *
     * By design, only DEBUG and VERBOSE are supported as override values. WARN and ERROR could
     * technically be detected (they make `isLoggable(INFO)` return `false`, unlike the default),
     * but using setprop to make logging *more restrictive* than the SDK default serves no practical
     * debugging purpose. INFO is indistinguishable from "no setprop" and is also intentionally
     * ignored. Use [Mindbox.setLogLevel] for fine-grained programmatic control.
     */
    private fun setPropLevel(): Level =
        if (Log.isLoggable(TAG, Log.DEBUG)) Level.DEBUG else Level.NONE

    /**
     * All the methods below should be used only after Mindbox.initComponents method was called
     */
    override fun i(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.INFO || setPropLevel() isAtMost Level.INFO) {
            Log.i(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun d(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.DEBUG || setPropLevel() isAtMost Level.DEBUG) {
            Log.d(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun e(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.ERROR || setPropLevel() isAtMost Level.ERROR) {
            Log.e(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun e(parent: Any, message: String, exception: Throwable) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.ERROR || setPropLevel() isAtMost Level.ERROR) {
            Log.e(TAG, logMessage, exception)
        }
        saveLog(logMessage + exception.stackTraceToString())
    }

    override fun w(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.WARN || setPropLevel() isAtMost Level.WARN) {
            Log.w(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun w(parent: Any, message: String, exception: Throwable) {
        val logMessage = buildMessage(parent, message)
        if (level isAtMost Level.WARN || setPropLevel() isAtMost Level.WARN) {
            Log.w(TAG, logMessage, exception)
        }
        saveLog(logMessage + exception.stackTraceToString())
    }

    private fun saveLog(message: String) {
        if (!MindboxDI.isInitialized()) return
        monitoringScope.launch {
            monitoringRepository.saveLog(
                Instant.now().convertToZonedDateTimeAtUTC(),
                message
            )
        }
    }

    private fun buildMessage(
        parent: Any,
        message: String,
    ) = "${parent.javaClass.simpleName}: $message"
}

internal fun Any.mindboxLogD(message: String) = MindboxLoggerImpl.d(this, message)

internal fun Any.mindboxLogI(message: String) = MindboxLoggerImpl.i(this, message)

internal fun Any.mindboxLogW(message: String, exception: Throwable? = null) = exception?.let {
    MindboxLoggerImpl.w(this, message, exception)
} ?: MindboxLoggerImpl.w(this, message)

internal fun Any.mindboxLogE(message: String, exception: Throwable? = null) = exception?.let {
    MindboxLoggerImpl.e(this, message, exception)
} ?: MindboxLoggerImpl.e(this, message)

internal interface MindboxLog {
    fun logD(message: String) = this.mindboxLogD(message)

    fun logI(message: String) = this.mindboxLogI(message)

    fun logW(message: String, exception: Throwable? = null) = this.mindboxLogW(message, exception)

    fun logE(message: String, exception: Throwable? = null) = this.mindboxLogE(message, exception)
}
