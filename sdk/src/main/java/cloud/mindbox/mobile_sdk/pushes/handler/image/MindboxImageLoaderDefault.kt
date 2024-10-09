package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import java.net.URL

/**
 * Default image loading implementation
 */
fun MindboxImageLoader.Companion.default(): MindboxImageLoader = DefaultLoader

private val DefaultLoader: MindboxImageLoader = MindboxImageLoaderDefault()

internal class MindboxImageLoaderDefault : MindboxImageLoader {

    companion object {

        private const val IMAGE_CONNECTION_TIMEOUT = 30_000
    }

    override fun onLoadImage(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
    ): Bitmap? {
        val imageUrl = message.imageUrl
        val logMessage = buildString {
            append("Image loading started, imageUrl=")
            append(imageUrl)
            if (imageUrl.isNullOrBlank()) {
                append(" (Image upload is not required)")
            }
        }
        MindboxLoggerImpl.d(this, logMessage)
        if (imageUrl.isNullOrBlank()) return null
        val connection = URL(imageUrl).openConnection().apply {
            readTimeout = IMAGE_CONNECTION_TIMEOUT
            connectTimeout = IMAGE_CONNECTION_TIMEOUT
        }
        val bitmap = BitmapFactory.decodeStream(connection.getInputStream())
        MindboxLoggerImpl.d(this, "Image successfully decoded, bitmap=$bitmap")
        return bitmap
    }
}
