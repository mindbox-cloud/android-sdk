package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URL

/**
 * Default image loading implementation
 */
public fun MindboxImageLoader.Companion.default(): MindboxImageLoader = DefaultLoader

private val DefaultLoader: MindboxImageLoader = MindboxImageLoaderDefault()

internal class MindboxImageLoaderDefault : MindboxImageLoader, PushImageLoader {

    companion object {

        private const val IMAGE_CONNECTION_TIMEOUT = 30_000
        private const val ANIMATED_IMAGE_MIN_SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        private val GIF_SIGNATURES = setOf("GIF87a", "GIF89a")
        private const val GIF_SIGNATURE_LENGTH = 6
    }

    override fun onLoadImage(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
    ): Bitmap? {
        val input = openImageStream(message) ?: return null
        val bitmap = BitmapFactory.decodeStream(input)
        MindboxLoggerImpl.d(this, "Image successfully decoded, bitmap=$bitmap")
        return bitmap
    }

    override fun loadPushImage(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        sdkInt: Int,
    ): PushImage? {
        val input = openImageStream(message)?.buffered() ?: return null
        return input.use {
            if (sdkInt >= ANIMATED_IMAGE_MIN_SDK && input.startsWithGifSignature()) {
                loadAnimatedImage(context, input)
            } else {
                BitmapFactory.decodeStream(input)?.let(::PushImage)
            }
        }.also { MindboxLoggerImpl.d(this, "Image successfully decoded, image=$it") }
    }

    private fun openImageStream(message: MindboxRemoteMessage): InputStream? {
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
        return connection.getInputStream()
    }

    private fun loadAnimatedImage(context: Context, input: InputStream): PushImage? {
        val file = PushImageFileStore.save(context, input)
        val firstFrame = BitmapFactory.decodeFile(file.path)
        if (firstFrame == null) {
            file.delete()
            return null
        }
        return PushImage(bitmap = firstFrame, animatedImageUri = PushImageFileStore.uriFor(context, file))
    }

    private fun BufferedInputStream.startsWithGifSignature(): Boolean {
        mark(GIF_SIGNATURE_LENGTH)
        val header = ByteArray(GIF_SIGNATURE_LENGTH)
        var read = 0
        while (read < GIF_SIGNATURE_LENGTH) {
            val count = read(header, read, GIF_SIGNATURE_LENGTH - read)
            if (count < 0) break
            read += count
        }
        reset()
        return read == GIF_SIGNATURE_LENGTH && String(header, Charsets.US_ASCII) in GIF_SIGNATURES
    }

    override fun toString(): String {
        return "DefaultLoader"
    }
}
