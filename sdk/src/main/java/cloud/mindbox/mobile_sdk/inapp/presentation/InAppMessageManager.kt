package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import com.google.gson.JsonElement
import kotlinx.coroutines.Job

internal interface InAppMessageManager {
    fun listenEventAndInApp()

    /**
     * The page's `showInApp`: opens the overlay in-app with [inAppId] immediately — past the
     * queue, the active-show lock, every limit and `delayTime`. Nothing is checked beyond the
     * version filter and "the variant is overlay-presentable": what the user just tapped has to
     * open. [extraParams] land in the shown page's start payload last, over whatever the SDK and
     * the configuration put there. Show/dismiss accounting runs the ordinary overlay path.
     */
    fun showInAppById(inAppId: String, extraParams: Map<String, JsonElement>)

    fun requestConfig(): Job

    fun registerCurrentActivity(activity: Activity)

    fun onPauseCurrentActivity(activity: Activity)

    fun onStopCurrentActivity(activity: Activity)

    fun registerInAppCallback(inAppCallback: InAppCallback)

    fun unregisterInAppCallback()

    fun initLogs()

    fun onResumeCurrentActivity(activity: Activity)

    fun handleSessionExpiration()
}
