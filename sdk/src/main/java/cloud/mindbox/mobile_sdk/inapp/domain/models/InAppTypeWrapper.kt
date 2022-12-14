package cloud.mindbox.mobile_sdk.inapp.domain.models

internal data class InAppTypeWrapper(
    val inAppType: InAppType,
    val onInAppClick: () -> Unit,
    val onInAppShown: () -> Unit,
)
