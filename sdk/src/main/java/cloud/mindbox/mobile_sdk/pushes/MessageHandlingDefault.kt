package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URL

/**
 * Default implementation [MessageHandlingCallback]
 */
open class MessageHandlingDefault : MessageHandlingCallback {

    companion object {

        private const val IMAGE_CONNECTION_TIMEOUT = 30_000

    }

    override fun onLoadImage(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
    ): Bitmap? = message.imageUrl?.let { url ->
        val connection = URL(url).openConnection().apply {
            readTimeout = IMAGE_CONNECTION_TIMEOUT
            connectTimeout = IMAGE_CONNECTION_TIMEOUT
        }
        BitmapFactory.decodeStream(connection.getInputStream())
    }

    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable,
    ): ImageRetryStrategy = ImageRetryStrategy.NoRetry()

}