package cloud.mindbox.mobile_sdk.inapp.presentation

internal class InAppCallbackWrapper(
    private val callbackProvider: () -> InAppCallback,
    private val afterDismiss: () -> Unit = {},
) : InAppCallback {

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        callbackProvider().onInAppClick(id, redirectUrl, payload)
    }

    override fun onInAppDismissed(id: String) {
        callbackProvider().onInAppDismissed(id)
        afterDismiss.invoke()
    }
}
