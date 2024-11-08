package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

/**
 * Ready-to-use implementation of InAppCallback that handles opening url in browser
 **/
open class UrlInAppCallback : InAppCallback {

    private val activityManager: ActivityManager by mindboxInject {
        activityManager
    }

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        activityManager.tryOpenUrl(redirectUrl)
    }

    override fun onInAppDismissed(id: String) {
        return
    }
}
