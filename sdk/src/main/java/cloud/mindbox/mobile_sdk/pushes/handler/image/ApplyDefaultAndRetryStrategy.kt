package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

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
): MindboxImageFailureHandler = ApplyDefaultAndRetryStrategyImpl(
    maxAttempts = maxAttempts,
    delay = delay,
    defaultImage = defaultImage,
)

@RequiresApi(Build.VERSION_CODES.M)
internal class ApplyDefaultAndRetryStrategyImpl(
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
        ImageRetryStrategy.ApplyDefaultAndRetry(delay = delay, defaultImage = defaultImage)
    }

}