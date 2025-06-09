package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown

internal interface InAppLifecycleCallbacks {
    val onInAppClick: OnInAppClick
    val onInAppShown: OnInAppShown
    val onInAppDismiss: OnInAppDismiss

    fun copy(onInAppShown: OnInAppShown): InAppLifecycleCallbacks = object : InAppLifecycleCallbacks {
        override val onInAppClick = this@InAppLifecycleCallbacks.onInAppClick
        override val onInAppShown = onInAppShown
        override val onInAppDismiss = this@InAppLifecycleCallbacks.onInAppDismiss
    }
}
