package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class LifecycleManager(
    private val onAppStarted: () -> Unit
) : Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {

    }

    override fun onActivityStarted(activity: Activity) {
        if (currentActivity?.javaClass?.name == activity.javaClass.name) {
            onAppStarted()
        } else {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {
        if (currentActivity == null) {
            currentActivity = activity
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

}
