package cloud.mindbox.mobile_sdk

import android.util.Log

internal object Logger {

    fun i(parent: Any, message: String) {
        Log.i(parent.javaClass.simpleName, message)
    }

    fun d(parent: Any, message: String) {
        Log.i(parent.javaClass.simpleName, message)
    }
}