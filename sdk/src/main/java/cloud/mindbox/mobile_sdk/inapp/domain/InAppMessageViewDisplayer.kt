package cloud.mindbox.mobile_sdk.inapp.domain

import android.app.Activity

interface InAppMessageViewDisplayer {

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun onPauseCurrentActivity(activity: Activity)

    suspend fun showInAppMessage(
        inAppType: InAppType,
        onInAppClick: () -> Unit,
        onInAppShown: () -> Unit,
    )

    fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean)
}