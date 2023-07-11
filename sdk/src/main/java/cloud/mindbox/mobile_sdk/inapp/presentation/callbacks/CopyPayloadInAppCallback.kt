package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.ClipboardManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

/**
 * Ready-to-use implementation of InAppCallback that handles copying non json/xml/url string
 * to clipboard
 **/
open class CopyPayloadInAppCallback : InAppCallback {

    private val callbackInteractor: CallbackInteractor by mindboxInject {
        callbackInteractor
    }

    private val clipboardManager: ClipboardManager by mindboxInject {
        clipboardManager
    }

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        if (callbackInteractor.shouldCopyString(payload)) {
            clipboardManager.copyToClipboard(payload)
        }
    }

    override fun onInAppDismissed(id: String) {
        return
    }

}