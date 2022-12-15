package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.inapp.domain.InAppType

internal data class InAppTypeWrapper(
    val inAppType: InAppType,
    val onInAppClick: () -> Unit,
    val onInAppShown: () -> Unit,
)
