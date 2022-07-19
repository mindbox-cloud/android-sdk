package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage

/**
 * Interface for handling errors when loading an image
 *
 * @see MindboxImageFailureHandler.Companion.applyDefaultStrategy
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