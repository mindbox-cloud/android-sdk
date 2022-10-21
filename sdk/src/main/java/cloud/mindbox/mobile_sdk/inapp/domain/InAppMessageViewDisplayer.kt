package cloud.mindbox.mobile_sdk.inapp.domain

import android.app.Activity

interface InAppMessageViewDisplayer {

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun onPauseCurrentActivity(activity: Activity)

    fun showInAppMessage(inAppType: InAppType)

    fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean)
}