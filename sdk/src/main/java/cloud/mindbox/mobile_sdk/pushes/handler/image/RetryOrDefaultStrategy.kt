package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * A strategy where [maxAttempts] attempts will be made to load an image at intervals
 * of [delay] milliseconds, and then, if all attempts fail, a message with the [defaultImage]
 * will be displayed to the user
 *
 * @param maxAttempts Maximum number of attempts to load an image
 * @param delay Period in milliseconds after which the download will be retried.
 * @param defaultImage Optional image
 */
fun MindboxImageFailureHandler.Companion.retryOrDefaultStrategy(
    maxAttempts: Int,
    delay: Long = 0L,
    defaultImage: Bitmap? = null,
): MindboxImageFailureHandler = RetryOrDefaultStrategyImpl(
    maxAttempts = maxAttempts,
    delay = delay,
    defaultImage = defaultImage,
)

internal class RetryOrDefaultStrategyImpl(
    private val maxAttempts: Int,
    private val delay: Long = 0L,
    private val defaultImage: Bitmap? = null,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy = if (state.attemptNumber >= maxAttempts) {
        ImageRetryStrategy.ApplyDefault(defaultImage = defaultImage)
    } else {
        ImageRetryStrategy.Retry(delay = delay)
    }

}