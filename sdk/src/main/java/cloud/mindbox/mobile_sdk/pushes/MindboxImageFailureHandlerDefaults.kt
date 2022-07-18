package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Default implementation of error handling on image loading
 */
val MindboxImageFailureHandler.Companion.Default: MindboxImageFailureHandler
    get() = DefaultStrategy

private val DefaultStrategy: MindboxImageFailureHandler = ApplyDefaultStrategyImpl()

/**
 * The strategy applies the passed default image
 *
 * @param defaultImage Optional image
 */
fun MindboxImageFailureHandler.Companion.applyDefaultImageStrategy(
    defaultImage: Bitmap? = null,
): MindboxImageFailureHandler {
    return ApplyDefaultStrategyImpl(defaultImage = defaultImage)
}

/**
 * Cancels the process, which means the message will not be shown to the user
 */
fun MindboxImageFailureHandler.Companion.cancellationStrategy(): MindboxImageFailureHandler {
    return CancellationStrategyImpl()
}

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
): MindboxImageFailureHandler {
    return RetryOrCancelStrategyImpl(
        maxAttempts = maxAttempts,
        delay = delay,
    )
}

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
): MindboxImageFailureHandler {
    return RetryOrDefaultStrategyImpl(
        maxAttempts = maxAttempts,
        delay = delay,
        defaultImage = defaultImage
    )
}

/**
 * A strategy that will display a message with a [defaultImage] and make [maxAttempts] attempts
 * to load the image at intervals of [delay] milliseconds.
 * If successful, the image in the displayed message will be updated.
 *
 * @param maxAttempts Maximum number of attempts to load an image
 * @param delay Period in milliseconds after which the download will be retried.
 * @param defaultImage Optional image
 */
@RequiresApi(Build.VERSION_CODES.M)
fun MindboxImageFailureHandler.Companion.applyDefaultAndRetryStrategy(
    maxAttempts: Int,
    delay: Long = 0L,
    defaultImage: Bitmap? = null,
): MindboxImageFailureHandler {
    return ApplyDefaultAndRetryStrategyImpl(
        maxAttempts = maxAttempts,
        delay = delay,
        defaultImage = defaultImage,
    )
}


internal class ApplyDefaultStrategyImpl(
    private val defaultImage: Bitmap? = null,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy {
        return ImageRetryStrategy.ApplyDefault(defaultImage = defaultImage)
    }

}

internal class CancellationStrategyImpl : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy {
        return ImageRetryStrategy.Cancel
    }

}

internal class RetryOrCancelStrategyImpl(
    private val maxAttempts: Int,
    private val delay: Long = 0L,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy {
        return if (state.attemptNumber >= maxAttempts) {
            ImageRetryStrategy.Cancel
        } else {
            ImageRetryStrategy.Retry(delay = delay)
        }
    }

}

internal class RetryOrDefaultStrategyImpl(
    private val maxAttempts: Int,
    private val delay: Long = 0L,
    private val defaultImage: Bitmap? = null,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy {
        return if (state.attemptNumber >= maxAttempts) {
            ImageRetryStrategy.ApplyDefault(defaultImage = defaultImage)
        } else {
            ImageRetryStrategy.Retry(delay = delay)
        }
    }

}

@RequiresApi(Build.VERSION_CODES.M)
internal class ApplyDefaultAndRetryStrategyImpl(
    private val maxAttempts: Int,
    private val delay: Long = 0L,
    private val defaultImage: Bitmap? = null,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy {
        return if (state.attemptNumber > maxAttempts) {
            ImageRetryStrategy.ApplyDefault(defaultImage = defaultImage)
        } else {
            ImageRetryStrategy.ApplyDefaultAndRetry(delay = delay, placeholder = defaultImage)
        }
    }

}