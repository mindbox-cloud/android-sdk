package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

internal class LifecycleManager(
    private val onAppStarted: () -> Unit
) : Application.ActivityLifecycleCallbacks, ComponentCallbacks2, LifecycleObserver {

    var currentActivity: Activity? = null

    private var isConfigurationChanged = false

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {
        Log.d("______", "created cur $currentActivity act $activity")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d("______", "cur $currentActivity act $activity action ${activity.intent.action} extas ${activity.intent.extras} data ${activity.intent.data} cat ${activity.intent.categories} schema ${activity.intent.scheme}")
activity.intent.scheme

        when {
            isConfigurationChanged -> isConfigurationChanged = false
            currentActivity?.javaClass?.name == activity.javaClass.name -> {
                Log.d("______", "app started")
                onAppStarted()
            }
            else -> currentActivity = activity
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
        Log.d("______", "stopped cur $currentActivity act $activity action ${activity.intent.action} extas ${activity.intent.extras} data ${activity.intent.data} cat ${activity.intent.categories}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d("______", "onConfigurationChanged")
        isConfigurationChanged = true
    }

    override fun onLowMemory() {

    }

    override fun onTrimMemory(level: Int) {

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppMovedToBackground() {
        Log.d("_______", "App in background $currentActivity action ${currentActivity?.intent?.action} extas ${currentActivity?.intent?.extras} data ${currentActivity?.intent?.data} cat ${currentActivity?.intent?.categories}")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppMovedToForeground() {
        Log.d("_______", "App in foreground $currentActivity action ${currentActivity?.intent?.action} extas ${currentActivity?.intent?.extras} data ${currentActivity?.intent?.data} cat ${currentActivity?.intent?.categories}")
    }

}
