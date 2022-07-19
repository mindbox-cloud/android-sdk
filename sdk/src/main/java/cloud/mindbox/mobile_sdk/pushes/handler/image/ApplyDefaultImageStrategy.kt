package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * The strategy applies the passed [defaultImage] if loading failed
 *
 * @param defaultImage Optional image
 */
fun MindboxImageFailureHandler.Companion.applyDefaultImageStrategy(
    defaultImage: Bitmap? = null,
): MindboxImageFailureHandler = ApplyDefaultStrategyImpl(defaultImage = defaultImage)

internal class ApplyDefaultStrategyImpl(
    private val defaultImage: Bitmap? = null,
) : MindboxImageFailureHandler {

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy = ImageRetryStrategy.ApplyDefault(defaultImage = defaultImage)

}