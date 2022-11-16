package cloud.mindbox.mobile_sdk.logger

import android.util.Log
import com.android.volley.VolleyLog

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

    init {

        VolleyLog.DEBUG = false

    }

    @Volatile
    internal var level: Level = DEFAULT_LOG_LEVEL

    override fun i(parent: Any, message: String) {
        if (level.value <= Level.INFO.value) {
            Log.i(TAG, buildMessage(parent, message))
        }
    }

    override fun d(parent: Any, message: String) {
        if (level.value <= Level.DEBUG.value) {
            Log.d(TAG, buildMessage(parent, message))
        }
    }

    override fun e(parent: Any, message: String) {
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, buildMessage(parent, message))
        }
    }

    override fun e(parent: Any, message: String, exception: Throwable) {
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, buildMessage(parent, message), exception)
        }
    }

    override fun w(parent: Any, message: String) {
        if (level.value <= Level.WARN.value) {
            Log.w(TAG, buildMessage(parent, message))
        }
    }

    override fun w(parent: Any, message: String, exception: Throwable) {
        if (level.value <= Level.WARN.value) {
            Log.w(TAG, buildMessage(parent, message), exception)
        }
    }

    private fun buildMessage(
        parent: Any,
        message: String
    ) = "${parent.javaClass.simpleName}: $message"

}
