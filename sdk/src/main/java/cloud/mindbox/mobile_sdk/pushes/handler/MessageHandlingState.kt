package cloud.mindbox.mobile_sdk.pushes.handler

import com.google.gson.annotations.SerializedName

/**
 * Current conditions under which the image is loaded
 *
 * @param attemptNumber The current number of attempts to correctly process the notification
 * @param isMessageDisplayed The message has been shown
 */
public data class MessageHandlingState(
    @SerializedName("attemptNumber")
    val attemptNumber: Int,
    @SerializedName("isMessageDisplayed")
    val isMessageDisplayed: Boolean,
)
