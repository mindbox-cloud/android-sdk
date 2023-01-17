package cloud.mindbox.mobile_sdk.logger

import android.util.Log
import cloud.mindbox.mobile_sdk.inapp.di.MindboxKoin
import cloud.mindbox.mobile_sdk.monitoring.MonitoringManager
import com.android.volley.VolleyLog
import kotlinx.coroutines.*
import java.time.Instant

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

    private val DEFAULT_LOG_LEVEL = Level.ERROR

    private val monitoringManager: MonitoringManager by lazy {
        MindboxKoin.koin.get(MonitoringManager::class)
    }

    private val monitoringScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Mindbox monitoring caught unhandled error", throwable)
        })

    init {

        VolleyLog.DEBUG = false

    }

    @Volatile
    internal var level: Level = DEFAULT_LOG_LEVEL

    override fun i(parent: Any, message: String) {
        if (level.value <= Level.INFO.value) {
            val logMessage = buildMessage(parent, message)
            Log.i(TAG, logMessage)
            saveLog(logMessage)
        }
    }

    override fun d(parent: Any, message: String) {
        if (level.value <= Level.DEBUG.value) {
            val logMessage = buildMessage(parent, message)
            Log.d(TAG, logMessage)
            saveLog(logMessage)
        }
    }

    override fun e(parent: Any, message: String) {
        if (level.value <= Level.ERROR.value) {
            val logMessage = buildMessage(parent, message)
            Log.e(TAG, logMessage)
            saveLog(logMessage)
        }
    }

    override fun e(parent: Any, message: String, exception: Throwable) {
        if (level.value <= Level.ERROR.value) {
            val logMessage = buildMessage(parent, message)
            Log.e(TAG, logMessage, exception)
            saveLog(logMessage)
        }
    }

    override fun w(parent: Any, message: String) {
        if (level.value <= Level.WARN.value) {
            val logMessage = buildMessage(parent, message)
            Log.w(TAG, logMessage)
            saveLog(logMessage)
        }
    }

    override fun w(parent: Any, message: String, exception: Throwable) {
        if (level.value <= Level.WARN.value) {
            val logMessage = buildMessage(parent, message)
            Log.w(TAG, logMessage, exception)
            saveLog(logMessage)
        }
    }

    private fun saveLog(message: String) {
        monitoringScope.launch {
            monitoringManager.saveLog(Instant.now().toEpochMilli(), message)
        }
    }

    private fun buildMessage(
        parent: Any,
        message: String,
    ) = "${parent.javaClass.simpleName}: $message"

}
