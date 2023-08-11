package cloud.mindbox.mobile_sdk.inapp.presentation

internal interface ActivityManager {

    fun tryOpenUrl(url: String): Boolean

    fun tryOpenDeepLink(deepLink: String): Boolean
}