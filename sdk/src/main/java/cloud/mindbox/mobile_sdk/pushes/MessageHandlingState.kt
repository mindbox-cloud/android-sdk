package cloud.mindbox.mobile_sdk.pushes

/**
 * Current conditions under which the image is loaded
 */
data class MessageHandlingState(
    /**
     * The current number of attempts to correctly process the notification
     */
    val attemptNumber: Int,
    /**
     * Notification has been shown
     */
    val isNotificationWasShown: Boolean
)