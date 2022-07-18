package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.graphics.Bitmap

/**
 * Helper for handling remote message
 *
 * @see MessageHandlingDefault
 */
interface MessageHandlingCallback {

    /**
     * Loading an image for notification
     *
     * @param context Android context
     * @param message Notification message
     * @param state Current state of message handling
     *
     * @return Bitmap or null.
     * If the bitmap is null, then the notification will be displayed without image
     *
     * @throws Throwable If the function throws an error, the [onImageLoadingFailed] function will be called.
     */
    fun onLoadImage(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
    ): Bitmap?

    /**
     * Called when an error occurs while executing [onLoadImage]
     *
     * @param context Android context
     * @param message Notification message
     * @param state Current state of message handling
     * @param error The error that occurred on [onLoadImage]
     *
     * @return Decision to be taken in case of an image load error
     *
     */
    fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy
}