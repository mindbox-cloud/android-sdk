package cloud.mindbox.mobile_sdk.inapp.presentation

/**
 * Interface for InApp message callbacks
 **/
public interface InAppCallback {

    /**
     * Method that triggers when user clicks on InApp message
     *
     * @param id - InApp message id
     *
     * @param redirectUrl - url for redirection.
     *
     * @param payload - additional data for InApp
     **/
    public fun onInAppClick(id: String, redirectUrl: String, payload: String)

    /**
     * Method that triggers when InApp message is dismissed by user
     *
     * @param id - InApp message id
     *
     **/
    public fun onInAppDismissed(id: String)
}
