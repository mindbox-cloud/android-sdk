package cloud.mindbox.mobile_sdk_core.logger

import android.util.Log
import com.android.volley.VolleyLog

internal object MindboxLogger {

    private val TAG = "Mindbox"
    private val DEFAULT_LOG_LEVEL = Level.ERROR

    init {

        VolleyLog.DEBUG = false

    }

    @Volatile
    internal var level: Level = DEFAULT_LOG_LEVEL

    fun i(parent: Any, message: String) {
        if (level.value <= Level.INFO.value) {
            Log.i(TAG, buildMessage(parent, message))
        }
    }

    fun d(parent: Any, message: String) {
        if (level.value <= Level.DEBUG.value) {
            Log.d(TAG, buildMessage(parent, message))
        }
    }

    fun e(parent: Any, message: String) {
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, buildMessage(parent, message))
        }
    }

    fun e(parent: Any, message: String, exception: Throwable) {
        if (level.value <= Level.ERROR.value) {
            Log.e(TAG, buildMessage(parent, message), exception)
        }
    }

    fun w(parent: Any, message: String) {
        if (level.value <= Level.WARN.value) {
            Log.w(TAG, buildMessage(parent, message))
        }
    }

    private fun buildMessage(
        parent: Any,
        message: String
    ) = "${parent.javaClass.simpleName}: $message"

}
