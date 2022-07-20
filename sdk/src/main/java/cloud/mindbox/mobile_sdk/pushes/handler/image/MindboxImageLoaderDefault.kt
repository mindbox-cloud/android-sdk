package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    ): Bitmap? = message.imageUrl?.let { url ->
        val connection = URL(url).openConnection().apply {
            readTimeout = IMAGE_CONNECTION_TIMEOUT
            connectTimeout = IMAGE_CONNECTION_TIMEOUT
        }
        BitmapFactory.decodeStream(connection.getInputStream())
    }

}