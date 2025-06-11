package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown

internal interface InAppActionCallbacks {
    val onInAppClick: OnInAppClick
    val onInAppShown: OnInAppShown
    val onInAppDismiss: OnInAppDismiss

    fun copy(
        onInAppClick: OnInAppClick = this.onInAppClick,
        onInAppShown: OnInAppShown = this.onInAppShown,
        onInAppDismiss: OnInAppDismiss = this.onInAppDismiss
    ): InAppActionCallbacks = object : InAppActionCallbacks {
        override val onInAppClick = onInAppClick
        override val onInAppShown = onInAppShown
        override val onInAppDismiss = onInAppDismiss
    }
}
