package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

internal interface InAppMessageViewDisplayer {

    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun onPauseCurrentActivity(activity: Activity)

    fun tryShowInAppMessage(
        inAppType: InAppType,
        onInAppClick: () -> Unit,
        onInAppShown: () -> Unit,
    )

    fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean)

    fun registerInAppCallback(inAppCallback: InAppCallback)
}