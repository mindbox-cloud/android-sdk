package cloud.mindbox.mobile_sdk.pushes.handler

/**
 * Current conditions under which the image is loaded
 *
 * @param attemptNumber The current number of attempts to correctly process the notification
 * @param isMessageDisplayed The message has been shown
 */
public data class MessageHandlingState(
    val attemptNumber: Int,
    val isMessageDisplayed: Boolean,
)
