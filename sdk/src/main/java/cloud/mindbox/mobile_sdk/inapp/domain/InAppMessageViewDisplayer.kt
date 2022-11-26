package cloud.mindbox.mobile_sdk.inapp.domain

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppConstraintLayout

internal interface InAppMessageViewDisplayer {

    var currentRoot: ViewGroup?
    var currentBlur: View?
    var currentDialog: InAppConstraintLayout?
    var currentActivity: Activity?
    var inAppCallback: InAppCallback?

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun onPauseCurrentActivity(activity: Activity)

    suspend fun showInAppMessage(
        inAppType: InAppType,
        onInAppClick: () -> Unit,
        onInAppShown: () -> Unit,
    )

    fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun registerInAppCallback(inAppCallback: InAppCallback)
}