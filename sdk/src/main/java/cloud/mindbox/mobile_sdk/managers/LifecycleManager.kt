package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.Mindbox.IS_OPENED_FROM_PUSH_BUNDLE_KEY
import cloud.mindbox.mobile_sdk.models.DIRECT
import cloud.mindbox.mobile_sdk.models.LINK
import cloud.mindbox.mobile_sdk.models.PUSH

internal class LifecycleManager(
    private var currentActivity: Activity?
) : Application.ActivityLifecycleCallbacks, ComponentCallbacks2, LifecycleObserver {

    companion object {

        private const val SCHEMA_HTTP = "http"
        private const val SCHEMA_HTTPS = "https"

    }

    private var isConfigurationChanged = false
    private var isAppInBackground = true

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {
        setActivityIfNeeded(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (isConfigurationChanged || isAppInBackground) {
            isConfigurationChanged = false
            isAppInBackground = false
            return
        }

        sendTrackVisit(activity)
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {
        setActivityIfNeeded(activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        isConfigurationChanged = true
    }

    override fun onLowMemory() {

    }

    override fun onTrimMemory(level: Int) {

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onAppMovedToBackground() {
        isConfigurationChanged = false
        isAppInBackground = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onAppMovedToForeground() {
        currentActivity?.let(::sendTrackVisit)
    }

    private fun setActivityIfNeeded(activity: Activity) {
        if (currentActivity == null) {
            currentActivity = activity
        }
    }

    private fun sendTrackVisit(activity: Activity) {
        val intent = activity.intent
        val source = source(intent)
        val requestUrl = if (source == LINK) intent?.data?.toString() else null

        if (currentActivity?.javaClass?.name == activity.javaClass.name || source != DIRECT) {
            Mindbox.sendTrackVisitEvent(activity, source, requestUrl)
        }
    }

    private fun source(intent: Intent?) = when {
        intent?.scheme == SCHEMA_HTTP || intent?.scheme == SCHEMA_HTTPS -> LINK
        intent?.extras?.getBoolean(IS_OPENED_FROM_PUSH_BUNDLE_KEY) == true -> PUSH
        else -> DIRECT
    }

}
