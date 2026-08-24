package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import com.google.gson.JsonElement

internal data class InAppTypeWrapper<out T : InAppType>(
    val inAppType: T,
    val inAppActionCallbacks: InAppActionCallbacks,
    val onRenderStart: () -> Unit,
    val tags: Map<String, String>? = null,
    val extraParams: Map<String, JsonElement> = emptyMap(),
    val isRequestedShow: Boolean = false,
)

internal fun interface OnInAppClick {
    fun onClick()
}

internal fun interface OnInAppShown {
    fun onShown()
}

internal fun interface OnInAppDismiss {
    fun onDismiss()
}
