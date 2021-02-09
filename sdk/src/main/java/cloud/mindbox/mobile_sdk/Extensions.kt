package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager

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

fun Result<Unit>.logOnException() {
    val exception = this.exceptionOrNull()
    if (exception != null) {
        try {
            Logger.e(Mindbox, "Mindbox caught unhandled error", exception)
            // todo log crash
        } catch (e: Throwable) { }
    }
}
