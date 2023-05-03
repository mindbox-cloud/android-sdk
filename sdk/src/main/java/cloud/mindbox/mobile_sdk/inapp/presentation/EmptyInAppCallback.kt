package cloud.mindbox.mobile_sdk.inapp.presentation


internal class EmptyInAppCallback: InAppCallback {

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        return
    }

    override fun onInAppDismissed(id: String) {
        return
    }
}