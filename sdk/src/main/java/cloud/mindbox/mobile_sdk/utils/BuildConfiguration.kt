package cloud.mindbox.mobile_sdk.utils

import android.content.Context
import android.content.pm.ApplicationInfo

object BuildConfiguration {

    fun isDebug(
        context: Context
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

}