package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

/**
 * Ready-to-use implementation of InAppCallback designed with composite pattern that handles
 * multiple different implementations at once
 **/
public open class ComposableInAppCallback : InAppCallback {

    protected val callbacks: List<InAppCallback>

    public constructor(callbacks: List<InAppCallback> = listOf()) {
        this.callbacks = callbacks
    }

    public constructor(vararg callbacks: InAppCallback) {
        this.callbacks = callbacks.toList()
    }

    public constructor(inAppCallback: InAppCallback) {
        callbacks = listOf(inAppCallback)
    }

    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        callbacks.forEach { callback ->
            callback.onInAppClick(id, redirectUrl, payload)
        }
    }

    override fun onInAppDismissed(id: String) {
        callbacks.forEach { callback ->
            callback.onInAppDismissed(id)
        }
    }
}
