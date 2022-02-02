package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import cloud.mindbox.mobile_sdk.Mindbox.IS_OPENED_FROM_PUSH_BUNDLE_KEY
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.DIRECT
import cloud.mindbox.mobile_sdk.models.LINK
import cloud.mindbox.mobile_sdk.models.PUSH
import cloud.mindbox.mobile_sdk.returnOnException
import java.util.*
import kotlin.concurrent.timer

internal class LifecycleManager(
    private var currentActivityName: String?,
    private var currentIntent: Intent?,
    private var onTrackVisitReady: (source: String?, requestUrl: String?) -> Unit,
    private var isAppInBackground: Boolean
) : Application.ActivityLifecycleCallbacks, LifecycleObserver {

    companion object {

        private const val SCHEMA_HTTP = "http"
        private const val SCHEMA_HTTPS = "https"

        private const val TIMER_PERIOD = 1200000L
        private const val MAX_INTENT_HASHES_SIZE = 50

    }

    private var isIntentChanged = true
    private var timer: Timer? = null
    private val intentHashes = mutableListOf<Int>()

    private var skipSendingTrackVisit = false

    override fun onActivityCreated(activity: Activity, p1: Bundle?) {

    }

    override fun onActivityStarted(activity: Activity) {
        runCatching {
            val areActivitiesEqual = currentActivityName == activity.javaClass.name
            val intent = activity.intent
            isIntentChanged = if (currentIntent != intent) {
                updateActivityParameters(activity)
                intent?.hashCode()?.let(::updateHashesList) ?: true
            } else {
                false
            }

            if (isAppInBackground || !isIntentChanged) {
                isAppInBackground = false
                return
            }

            sendTrackVisit(activity.intent, areActivitiesEqual)
        }.logOnException()
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {
        if (currentIntent == null || currentActivityName == null) {
            updateActivityParameters(activity)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

    fun isTrackVisitSent(): Boolean {
        currentIntent?.let(::sendTrackVisit)
        return currentIntent != null
    }

    fun wasReinitialized() {
        skipSendingTrackVisit = true
    }

    fun onNewIntent(newIntent: Intent?) = newIntent?.let { intent ->
        if (intent.data != null || intent.extras?.get(IS_OPENED_FROM_PUSH_BUNDLE_KEY) == true) {
            isIntentChanged = updateHashesList(intent.hashCode())
            sendTrackVisit(intent)
            skipSendingTrackVisit = isAppInBackground
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onAppMovedToBackground() {
        isAppInBackground = true
        cancelKeepAliveTimer()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onAppMovedToForeground() = if (!skipSendingTrackVisit) {
        currentIntent?.let(::sendTrackVisit)
    } else {
        skipSendingTrackVisit = false
    }

    private fun updateActivityParameters(activity: Activity) = runCatching {
        currentActivityName = activity.javaClass.name
        currentIntent = activity.intent
    }.logOnException()

    private fun sendTrackVisit(intent: Intent, areActivitiesEqual: Boolean = true) = runCatching {
        val source = if (isIntentChanged) source(intent) else DIRECT

        if (areActivitiesEqual || source != DIRECT) {
            val requestUrl = if (source == LINK) intent.data?.toString() else null
            onTrackVisitReady.invoke(source, requestUrl)
            startKeepAliveTimer()

            MindboxLoggerImpl.d(this, "Track visit event with source $source and url $requestUrl")
        }
    }.logOnException()

    private fun source(intent: Intent?) = runCatching {
        when {
            intent?.scheme == SCHEMA_HTTP || intent?.scheme == SCHEMA_HTTPS -> LINK
            intent?.extras?.getBoolean(IS_OPENED_FROM_PUSH_BUNDLE_KEY) == true -> PUSH
            else -> DIRECT
        }
    }.returnOnException { null }

    private fun updateHashesList(code: Int) = runCatching {
        if (!intentHashes.contains(code)) {
            if (intentHashes.size >= MAX_INTENT_HASHES_SIZE) {
                intentHashes.removeAt(0)
            }
            intentHashes.add(code)
            true
        } else {
            false
        }
    }.returnOnException { true }

    private fun startKeepAliveTimer() = runCatching {
        cancelKeepAliveTimer()
        timer = timer(
            initialDelay = TIMER_PERIOD,
            period = TIMER_PERIOD,
            action = { onTrackVisitReady.invoke(null, null) }
        )
    }.logOnException()

    private fun cancelKeepAliveTimer() = runCatching {
        timer?.cancel()
        timer = null
    }.logOnException()

}
