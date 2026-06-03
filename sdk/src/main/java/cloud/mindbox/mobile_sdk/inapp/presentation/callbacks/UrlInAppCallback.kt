package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

/**
 * Ready-to-use implementation of [InAppCallback] that opens only http/https links.
 *
 * The link is opened via an `ACTION_VIEW` intent and the system chooses the handler: a regular
 * web link goes to the browser, while a verified Android App Link may be opened inside the
 * corresponding app. Non-http/https schemes (e.g. custom deep links) are ignored — use
 * [DeepLinkInAppCallback] for those.
 *
 * Do NOT combine this callback with [DeepLinkInAppCallback] in the same chain: both would issue
 * `startActivity` for the same http/https link, opening it twice.
 **/
public open class UrlInAppCallback : InAppCallback {

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
