package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URL

/**
 * Helper for handling remote message
 */
interface MessageHandlingCallback {

    /**
     * Loading an image for notification
     *
     * @param message Notification message
     * @param state Current state of message handling
     *
     * @return Bitmap or null.
     * If the bitmap is null, then the notification will be displayed without image
     *
     * @throws Throwable If the function throws an error, the [onImageLoadingFailed] function will be called.
     */
    fun onLoadImage(context: Context, message: RemoteMessage, state: MessageHandlingState): Bitmap?

    /**
     * Called when an error occurs while executing [onLoadImage]
     *
     * @param message Notification message
     * @param state Current state of message handling
     * @param error The error that occurred on [onLoadImage]
     *
     * @return Decision to be taken in case of an image load error
     *
     */
    fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable
    ): ImageFallback


    open class Default : MessageHandlingCallback {

        override fun onLoadImage(
            context: Context,
            message: RemoteMessage,
            state: MessageHandlingState
        ): Bitmap? {
            val url = message.imageUrl ?: return null
            val connection = URL(url).openConnection().apply {
                readTimeout = IMAGE_CONNECTION_TIMEOUT
                connectTimeout = IMAGE_CONNECTION_TIMEOUT
            }
            return BitmapFactory.decodeStream(connection.getInputStream())
        }

        override fun onImageLoadingFailed(
            context: Context,
            message: RemoteMessage,
            state: MessageHandlingState,
            error: Throwable
        ): ImageFallback {
            return ImageFallback.Allow()
        }


        companion object {
            private const val IMAGE_CONNECTION_TIMEOUT = 30_000
        }

    }

}