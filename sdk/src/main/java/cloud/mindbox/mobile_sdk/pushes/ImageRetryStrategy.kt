package cloud.mindbox.mobile_sdk.pushes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Solution for failed image downloading
 */
sealed class ImageRetryStrategy {

    /**
     * Cancel the process that needed the image
     */
    object Cancel : ImageRetryStrategy()

    /**
     * Continue the process without the desired image
     *
     * @param defaultImage An optional image to replace the desired one if the download fails
     *
     * @see BitmapFactory.decodeResource
     * @see androidx.core.graphics.drawable.toBitmap
     */
    data class ApplyDefault(
        val defaultImage: Bitmap? = null,
    ) : ImageRetryStrategy()

    /**
     * Retry image downloading
     *
     * @param delay Delay before retry in milliseconds
     */
    data class Retry(
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
    data class ApplyDefaultAndRetry(
        val delay: Long = 0L,
        val defaultImage: Bitmap? = null,
    ) : ImageRetryStrategy()

}