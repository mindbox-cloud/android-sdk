package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks

internal data class InAppTypeWrapper<out T : InAppType>(
    val inAppType: T,
    val inAppActionCallbacks: InAppActionCallbacks
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
