package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType

internal interface InAppMessageViewDisplayer {

    fun onResumeCurrentActivity(activity: Activity, isNeedToShow: () -> Boolean, onAppResumed: () -> Unit)

    fun onPauseCurrentActivity(activity: Activity)

    fun onStopCurrentActivity(activity: Activity)

    fun tryShowInAppMessage(
        inAppType: InAppType,
        inAppActionCallbacks: InAppActionCallbacks
    )

    fun registerCurrentActivity(activity: Activity)

    fun registerInAppCallback(inAppCallback: InAppCallback)

    fun isInAppActive(): Boolean

    fun hideCurrentInApp()
}
