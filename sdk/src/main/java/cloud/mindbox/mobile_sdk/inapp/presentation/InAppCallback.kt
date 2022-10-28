package cloud.mindbox.mobile_sdk.inapp.presentation

interface InAppCallback {

    fun onInAppClick(id: String, redirectUrl: String, payload: String)

    fun onInAppClosed(id: String)
}