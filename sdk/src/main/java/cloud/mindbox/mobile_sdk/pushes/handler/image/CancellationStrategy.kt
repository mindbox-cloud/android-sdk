package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * Cancels the process, which means that the message will not be shown to the user
 * if the image download fails
 */
public fun MindboxImageFailureHandler.Companion.cancellationStrategy(): MindboxImageFailureHandler =
    CancellationStrategyImpl()

internal class CancellationStrategyImpl : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy = ImageRetryStrategy.Cancel
}
