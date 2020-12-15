package cloud.mindbox.mobile_sdk

import android.util.Log

internal object Logger {

    fun i(parent: Any, message: String) {
        Log.i(parent.javaClass.simpleName, message)
    }

    fun d(parent: Any, message: String) {
        Log.d(parent.javaClass.simpleName, message)
    }

    fun e(parent: Any, message: String) {
        Log.e(parent.javaClass.simpleName, message)
    }

    fun w(parent: Any, message: String) {
        Log.w(parent.javaClass.simpleName, message)
    }
}