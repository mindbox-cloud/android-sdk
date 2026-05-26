package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppContentFetchingError
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.maxScreenDimension
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class InAppGlideImageLoaderImpl(
    private val context: Context,
    private val inAppImageSizeStorage: InAppImageSizeStorage
) : InAppImageLoader {

    private val requests = HashMap<String, Target<Drawable>>()

    override suspend fun loadImage(inAppId: String, url: String): Boolean {
        mindboxLogI("Loading image for inapp with id $inAppId started")
        val timeoutMs = context.getString(R.string.mindbox_inapp_fetching_timeout).toLong()
        val maxDim = context.maxScreenDimension()
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    requests[inAppId] = startPreload(inAppId, url, maxDim, timeoutMs.toInt(), continuation)
                    continuation.invokeOnCancellation { cancelLoading(inAppId) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            mindboxLogE("Image loading timed out after ${timeoutMs}ms for inapp $inAppId", e)
            throw InAppContentFetchingError(null)
        }
    }

    private fun startPreload(
        inAppId: String,
        url: String,
        maxDim: Int,
        timeoutMs: Int,
        continuation: CancellableContinuation<Boolean>,
    ): Target<Drawable> = Glide.with(context)
        .load(url)
        .timeout(timeoutMs)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .override(maxDim, maxDim)
        .centerInside()
        .listener(buildRequestListener(inAppId, url, continuation))
        .preload(maxDim, maxDim)

    private fun buildRequestListener(
        inAppId: String,
        url: String,
        continuation: CancellableContinuation<Boolean>,
    ): RequestListener<Drawable> = object : RequestListener<Drawable> {

        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>?,
            isFirstResource: Boolean,
        ): Boolean {
            mindboxLogI("Image loading failed for inapp $inAppId, url = $url")
            requests.remove(inAppId)
            if (continuation.isActive) {
                continuation.resumeWithException(InAppContentFetchingError(e))
            }
            return true
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any?,
            target: Target<Drawable>?,
            dataSource: DataSource?,
            isFirstResource: Boolean,
        ): Boolean {
            mindboxLogI("Image loading succeeded for inapp $inAppId, url = $url")
            if (!continuation.isActive) return true
            requests.remove(inAppId)
            return runCatching {
                val bitmap = resource.toBitmap()
                inAppImageSizeStorage.addSize(inAppId, url, bitmap.width, bitmap.height)
                continuation.resume(true)
            }.onFailure { e ->
                mindboxLogE("Failed to process loaded image for inapp $inAppId", e)
                continuation.resumeWithException(InAppContentFetchingError(null))
            }.isSuccess
        }
    }

    override fun cancelLoading(inAppId: String) {
        Glide.with(context).clear(requests[inAppId])
        requests.remove(inAppId)
    }
}
