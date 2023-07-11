package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
/**
 * Ready-to-use implementation of InAppCallback that handles logging
 **/
open class LoggingInAppCallback : InAppCallback {

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        mindboxLogI("Click on InApp with id = $id, redirectUrl = $redirectUrl and payload = $payload")
    }

    override fun onInAppDismissed(id: String) {
        mindboxLogI("Dismiss inApp with id = $id")
    }
}