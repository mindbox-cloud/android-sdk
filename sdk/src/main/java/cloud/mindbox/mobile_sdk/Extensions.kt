package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import java.util.*

internal fun Context.schedulePeriodicService() {
    val application = this.applicationContext as Application
    application.registerActivityLifecycleCallbacks(object :
        Application.ActivityLifecycleCallbacks {

        private var activityCount: Int = 0

        override fun onActivityResumed(activity: Activity) {
            activityCount++
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityDestroyed(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityPaused(activity: Activity) {
            activityCount--
            if (activityCount <= 0) {
                runCatching {
                    BackgroundWorkManager.startPeriodicService(applicationContext)
                }.logOnException()
            }
        }
    })

    this.registerComponentCallbacks(object : ComponentCallbacks2 {
        override fun onLowMemory() {}

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                runCatching {
                    BackgroundWorkManager.startPeriodicService(applicationContext)
                }.logOnException()
            }
        }
    })
}

internal fun <T> Result<T>.returnOnException(block: (exception: Throwable) -> T): T {
    return this.getOrElse { exception ->
        exception.handle()
        return block.invoke(exception)
    }
}

internal fun Result<Unit>.logOnException() {
    this.exceptionOrNull()?.handle()
}

private fun Throwable.handle() {
    try {
        MindboxLogger.e(Mindbox, "Mindbox caught unhandled error", this)
        // todo log crash
    } catch (e: Throwable) {
    }
}

internal fun String.isUuid(): Boolean {
    return if (this.trim().isNotEmpty()) {
        try {
            UUID.fromString(this)
            true
        } catch (e: Exception) {
            false
        }
    } else {
        false
    }
}

internal fun Map<String, String>.toUrlQueryString() = runCatching {
    return this.map { (k, v) -> "$k=$v" }
        .joinToString(prefix = "?", separator = "&")
}.returnOnException { "" }