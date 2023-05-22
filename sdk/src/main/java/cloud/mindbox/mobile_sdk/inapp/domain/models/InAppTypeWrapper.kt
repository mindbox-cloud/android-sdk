package cloud.mindbox.mobile_sdk.inapp.domain.models


internal data class InAppTypeWrapper<T: InAppType>(
    val inAppType: T,
    val onInAppClick: OnInAppClick,
    val onInAppShown: OnInAppShown,
)

internal fun interface OnInAppClick {
    fun onClick()
}

internal fun interface OnInAppShown {
    fun onShown()
}