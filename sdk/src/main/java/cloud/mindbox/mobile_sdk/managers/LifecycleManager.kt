package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class LifecycleManager(
    private val onAppStarted: () -> Unit
) : Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {

    }

    override fun onActivityStarted(p0: Activity) {
        if (currentActivity == p0) {
            onAppStarted()
        } else {
            currentActivity = p0
        }
    }

    override fun onActivityResumed(p0: Activity) {

    }

    override fun onActivityPaused(p0: Activity) {

    }

    override fun onActivityStopped(p0: Activity) {
        if (currentActivity == null) {
            currentActivity = p0
        }
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {

    }

    override fun onActivityDestroyed(p0: Activity) {

    }

}
