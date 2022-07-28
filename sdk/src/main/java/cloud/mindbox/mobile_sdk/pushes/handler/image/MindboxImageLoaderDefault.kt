package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
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
        message: RemoteMessage,
        state: MessageHandlingState,
    ): Bitmap? {
        val imageUrl = message.imageUrl
        val logMessage = buildString {
            append("Image loading started, imageUrl=")
            append(imageUrl)
            if (imageUrl == null) {
                append(" (Image upload is not required)")
            }
        }
        MindboxLoggerImpl.d(this, logMessage)
        if (imageUrl == null) return null
        val connection = URL(imageUrl).openConnection().apply {
            readTimeout = IMAGE_CONNECTION_TIMEOUT
            connectTimeout = IMAGE_CONNECTION_TIMEOUT
        }
        val bytes = connection.getInputStream().use { input -> input.readBytes() }
        MindboxLoggerImpl.d(this, "Loading complete, image size - ${bytes.size}")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        MindboxLoggerImpl.d(this, "Image successfully decoded, bitmap=$bitmap")
        return bitmap
    }

}