package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import com.google.gson.JsonElement
import kotlinx.coroutines.Job

internal interface InAppMessageManager {
    fun listenEventAndInApp()

    fun showInAppById(
        inAppId: String,
        extraParams: Map<String, JsonElement>,
        requesterIsActive: () -> Boolean,
        onOutcome: OnShowInAppOutcome,
    )

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
