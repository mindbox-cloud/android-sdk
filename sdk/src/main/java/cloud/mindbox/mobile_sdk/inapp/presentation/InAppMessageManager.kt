package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import kotlinx.coroutines.Job

internal interface InAppMessageManager {
    fun listenEventAndInApp()
    fun requestConfig(): Job
    fun registerCurrentActivity(activity: Activity)
    fun onPauseCurrentActivity(activity: Activity)
    fun registerInAppCallback(inAppCallback: InAppCallback)
    fun initInAppMessages()
    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)
}