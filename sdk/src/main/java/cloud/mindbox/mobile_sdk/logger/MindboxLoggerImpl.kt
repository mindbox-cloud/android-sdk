package cloud.mindbox.mobile_sdk.logger

import android.util.Log
import cloud.mindbox.mobile_sdk.convertToString
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
interface MindboxLogger {

    fun i(parent: Any, message: String)

    fun d(parent: Any, message: String)

    fun e(parent: Any, message: String)

    fun e(parent: Any, message: String, exception: Throwable)

    fun w(parent: Any, message: String)

    fun w(parent: Any, message: String, exception: Throwable)
}

internal object MindboxLoggerImpl : MindboxLogger {

    private const val TAG = "Mindbox"

    private val DEFAULT_LOG_LEVEL = Level.WARN

    private val monitoringRepository: MonitoringRepository by mindboxInject { monitoringRepository }

    val monitoringScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Mindbox monitoring caught unhandled error", throwable)
                }
        )

    init {
        VolleyLog.DEBUG = false
    }

    @Volatile
    internal var level: Level = DEFAULT_LOG_LEVEL

    /**
     * All the methods below should be used only after Mindbox.initComponents method was called
     */

    override fun i(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.INFO.value) {
            Log.i(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun d(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.DEBUG.value) {
            Log.d(TAG, logMessage)
        }
        addQueue(logMessage)
    }

    override fun e(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun e(parent: Any, message: String, exception: Throwable) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, logMessage, exception)
        }
        saveLog(logMessage + exception.stackTraceToString())
    }

    override fun w(parent: Any, message: String) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.WARN.value) {
            Log.w(TAG, logMessage)
        }
        saveLog(logMessage)
    }

    override fun w(parent: Any, message: String, exception: Throwable) {
        val logMessage = buildMessage(parent, message)
        if (level.value <= Level.WARN.value) {
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
            Log.d("MY_TAG_TRACK", "saveLog: END ${System.currentTimeMillis()}")
        }
    }

    private fun addQueue(message: String) {
        if (!MindboxDI.isInitialized()) return

        monitoringScope.launch {
            monitoringRepository.addLogQueue(
                Instant.now().convertToZonedDateTimeAtUTC().convertToString(),
                message
            )
            monitoringRepository.saveLogQueue()
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
