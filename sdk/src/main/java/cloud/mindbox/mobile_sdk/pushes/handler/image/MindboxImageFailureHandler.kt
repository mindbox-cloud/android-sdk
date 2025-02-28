package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * Interface for handling errors when loading an image
 *
 * @see MindboxImageFailureHandler.Companion.applyDefaultStrategy
 * @see MindboxImageFailureHandler.Companion.cancellationStrategy
 * @see MindboxImageFailureHandler.Companion.retryOrCancelStrategy
 * @see MindboxImageFailureHandler.Companion.retryOrDefaultStrategy
 * @see MindboxImageFailureHandler.Companion.applyDefaultAndRetryStrategy
 */
public interface MindboxImageFailureHandler {

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
    public fun onImageLoadingFailed(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy

    public companion object
}
