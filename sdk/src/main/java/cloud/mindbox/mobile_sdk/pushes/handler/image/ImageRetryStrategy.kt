package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Solution for failed image downloading
 */
public sealed class ImageRetryStrategy {

    /**
     * Cancel the process that needed the image
     */
    public object Cancel : ImageRetryStrategy() {
        override fun toString(): String = "Cancel"
    }

    /**
     * Continue the process without the desired image
     *
     * @param defaultImage An optional image to replace the desired one if the download fails
     *
     * @see BitmapFactory.decodeResource
     * @see androidx.core.graphics.drawable.toBitmap
     */
    public data class ApplyDefault(
        val defaultImage: Bitmap? = null,
    ) : ImageRetryStrategy()

    /**
     * Retry image downloading
     *
     * @param delay Delay before retry in milliseconds
     */
    public data class Retry(
        val delay: Long = 0L,
    ) : ImageRetryStrategy()

    /**
     * Continue the process without the desired image and then try downloading it again
     * This only works correctly on SDK >= 23
     *
     * @param delay Delay before retry in milliseconds
     * @param defaultImage An optional image to replace the desired one if the download fails
     *
     * @see BitmapFactory.decodeResource
     * @see androidx.core.graphics.drawable.toBitmap
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public data class ApplyDefaultAndRetry(
        val delay: Long = 0L,
        val defaultImage: Bitmap? = null,
    ) : ImageRetryStrategy()
}
