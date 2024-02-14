package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * A strategy where [maxAttempts] attempts will be made to load the image at [delay] millisecond
 * intervals, and then if all attempts fail, the process will be canceled and the user will not
 * see this message.
 *
 * @param maxAttempts Maximum number of attempts to load an image
 * @param delay Period in milliseconds after which the download will be retried.
 */
fun MindboxImageFailureHandler.Companion.retryOrCancelStrategy(
    maxAttempts: Int,
    delay: Long = 0L,
): MindboxImageFailureHandler = RetryOrCancelStrategyImpl(
    maxAttempts = maxAttempts,
    delay = delay,
)

internal class RetryOrCancelStrategyImpl(
    private val maxAttempts: Int,
    private val delay: Long = 0L,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy = if (state.attemptNumber >= maxAttempts) {
        ImageRetryStrategy.Cancel
    } else {
        ImageRetryStrategy.Retry(delay = delay)
    }

}