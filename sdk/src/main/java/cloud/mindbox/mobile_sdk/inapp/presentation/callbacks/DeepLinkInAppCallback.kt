package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

/**
 * Ready-to-use implementation of [InAppCallback] that opens a link of any scheme if there is an
 * activity able to handle it.
 *
 * The link is opened via an `ACTION_VIEW` intent and the system decides where it goes: a custom
 * deep link or a verified Android App Link is opened inside the app, while a regular http/https
 * link is opened in the browser. In other words, this callback covers both deep links and plain
 * web urls, so it is the only link-opening callback needed in the default chain.
 *
 * Do NOT combine it with [UrlInAppCallback] in the same chain: both would issue `startActivity`
 * for the same http/https link, opening it twice.
 **/
public open class DeepLinkInAppCallback : InAppCallback {

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
