package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

/**
 * Ready-to-use implementation of InAppCallback designed with composite pattern that handles
 * multiple different implementations at once
 **/
open class ComposableInAppCallback :
    InAppCallback {

    val callbacks: MutableList<InAppCallback> = mutableListOf()

    constructor(callbacks: MutableList<InAppCallback> = mutableListOf()) {
        this.callbacks.addAll(callbacks)
    }

    constructor(inAppCallback: InAppCallback) {
        callbacks.add(inAppCallback)
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

    override fun plus(term: InAppCallback): InAppCallback {
        return ComposableInAppCallback(mutableListOf<InAppCallback>().apply {
            addAll(callbacks)
            if (term !is ComposableInAppCallback) add(term) else addAll(term.callbacks)
        })
    }
}