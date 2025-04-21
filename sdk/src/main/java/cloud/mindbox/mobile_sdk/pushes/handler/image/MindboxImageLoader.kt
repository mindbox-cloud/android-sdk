package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

/**
 * Interface for loading an image from a message
 *
 * @see MindboxImageLoader.Companion.default
 */
interface MindboxImageLoader {

    companion object

    /**
     * Loading an image from a message
     *
     * @param context Android context
     * @param message Notification message
     * @param state Current state of message handling
     *
     * @return Bitmap or null.
     * If the bitmap is null, then the message will be displayed without image
     *
     * @throws Throwable If an exception is thrown, it will be passed to [MindboxImageFailureHandler]
     */
    fun onLoadImage(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
    ): Bitmap?
}
