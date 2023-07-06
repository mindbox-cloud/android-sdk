package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

/**
 * Interface for InApp message callbacks
 **/
interface InAppCallback {


    /**
     * Method that triggers when user clicks on InApp message
     *
     * @param id - InApp message id
     *
     * @param redirectUrl - url for redirection.
     *
     * @param payload - additional data for InApp
     **/
    fun onInAppClick(id: String, redirectUrl: String, payload: String)

    /**
     * Method that triggers when InApp message is dismissed by user
     *
     * @param id - InApp message id
     *
     **/
    fun onInAppDismissed(id: String)


    operator fun plus(term: InAppCallback): InAppCallback {
        if (term is ComposableInAppCallback) return ComposableInAppCallback(mutableListOf<InAppCallback>().apply {
            add(this@InAppCallback)
            addAll(term.callbacks)
        })
        return ComposableInAppCallback(mutableListOf(this, term))
    }
}