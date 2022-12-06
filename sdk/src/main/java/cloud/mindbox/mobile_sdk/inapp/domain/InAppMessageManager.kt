package cloud.mindbox.mobile_sdk.inapp.domain

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

internal interface InAppMessageManager {
    fun listenEventAndInApp()
    fun requestConfig()
    fun registerCurrentActivity(activity: Activity)
    fun onPauseCurrentActivity(activity: Activity)
    fun registerInAppCallback(inAppCallback: InAppCallback)
    fun initInAppMessages()
    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)
}