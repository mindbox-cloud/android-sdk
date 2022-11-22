package cloud.mindbox.mobile_sdk.inapp.domain

import android.app.Activity
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

internal interface InAppMessageManager {
    fun listenEventAndInApp(configuration: MindboxConfiguration)
    fun requestConfig(configuration: MindboxConfiguration)
    fun registerCurrentActivity(activity: Activity)
    fun onPauseCurrentActivity(activity: Activity)
    fun registerInAppCallback(inAppCallback: InAppCallback)
    fun initInAppMessages(configuration: MindboxConfiguration)
    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)
}