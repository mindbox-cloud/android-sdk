package cloud.mindbox.mobile_sdk.pushes

import android.content.Context

/**
 * Interface for handling errors when loading an image
 *
 * @see MindboxImageFailureHandler.Companion.applyDefaultImageStrategy
 * @see MindboxImageFailureHandler.Companion.cancellationStrategy
 * @see MindboxImageFailureHandler.Companion.retryOrCancelStrategy
 * @see MindboxImageFailureHandler.Companion.retryOrDefaultStrategy
 * @see MindboxImageFailureHandler.Companion.applyDefaultAndRetryStrategy
 */
interface MindboxImageFailureHandler {

    companion object

    /**
     * Called when an image loading error occurs.
     *
     * @param context Android context
     * @param message Notification message
     * @param state Current state of message handling
     * @param error Image loading error
     *
     * @return Strategy for error handling
     *
     */
    fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy

}