package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import com.google.gson.JsonElement

internal interface InAppMessageViewDisplayer {

    fun onResumeCurrentActivity(activity: Activity, isNeedToShow: () -> Boolean, onAppResumed: () -> Unit)

    fun onPauseCurrentActivity(activity: Activity)

    fun onStopCurrentActivity(activity: Activity)

    fun tryShowInAppMessage(
        inAppType: InAppType,
        inAppActionCallbacks: InAppActionCallbacks,
        onRenderStart: () -> Unit = {},
        tags: Map<String, String>? = null,
    )

    fun showInAppMessageNow(
        inAppType: InAppType,
        inAppActionCallbacks: InAppActionCallbacks,
        onRenderStart: () -> Unit = {},
        tags: Map<String, String>? = null,
        extraParams: Map<String, JsonElement> = emptyMap(),
    )

    fun registerCurrentActivity(activity: Activity)

    fun registerInAppCallback(inAppCallback: InAppCallback)

    fun unregisterInAppCallback()

    fun isInAppActive(): Boolean

    fun dismissCurrentInApp()
}
