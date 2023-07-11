package cloud.mindbox.mobile_sdk.inapp.presentation


internal class InAppCallbackWrapper(
    private val callback: InAppCallback,
    private val afterDismiss: () -> Unit = {},
): InAppCallback {

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        callback.onInAppClick(id, redirectUrl, payload)
    }

    override fun onInAppDismissed(id: String) {
        callback.onInAppDismissed(id)
        afterDismiss.invoke()
    }
}