package cloud.mindbox.mobile_sdk.pushes

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Solution for failed image downloading
 */
sealed class ImageFallback {

    /**
     * Cancel the process that needed the image
     */
    object Drop : ImageFallback()

    /**
     * Continue the process without the desired image
     */
    data class Allow(
        /**
         * Optional image to replace the desired one
         */
        val placeholder: Bitmap? = null
    ) : ImageFallback()

    /**
     * Retry image downloading
     */
    data class Retry(
        /**
         * Delay before retry in milliseconds
         */
        val delay: Long = 0L
    ) : ImageFallback()

    /**
     * Continue the process without the desired image and then try downloading it again
     * This only works correctly on SDK >= 23
     */
    @RequiresApi(Build.VERSION_CODES.M)
    data class AllowAndRetry(
        /**
         * Delay before retry in milliseconds
         */
        val delay: Long = 0L,
        /**
         * Optional image to replace the desired one
         */
        val placeholder: Bitmap? = null
    ) : ImageFallback()

}