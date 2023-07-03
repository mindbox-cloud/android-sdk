package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
/**
 * Ready-to-use implementation of InAppCallback that handles opening deeplink if it's possible
 **/
open class DeepLinkInAppCallback : InAppCallback {

    private val activityManager: ActivityManager by mindboxInject {
        activityManager
    }

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        activityManager.tryOpenDeepLink(redirectUrl)
    }

    override fun onInAppDismissed(id: String) {
        return
    }
}