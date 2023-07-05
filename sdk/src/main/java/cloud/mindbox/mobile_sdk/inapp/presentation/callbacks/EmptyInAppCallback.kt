package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

/***
 * Default InAppCallback for remove nullable in InAppMessageViewDisplayerImpl
 */
open class EmptyInAppCallback: InAppCallback {

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        return
    }

    override fun onInAppDismissed(id: String) {
        return
    }
}