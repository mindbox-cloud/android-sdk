package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppContentFetchingError
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class InAppGlideImageLoaderImpl(
    private val context: Context,
    private val inAppImageSizeStorage: InAppImageSizeStorage
) : InAppImageLoader {

    private val requests = HashMap<String, Target<Drawable>>()


    override suspend fun loadImage(inAppId: String, url: String): Boolean {
        mindboxLogD("loading image for inapp with id $inAppId started")
        return suspendCancellableCoroutine { cancellableContinuation ->
            val target = Glide.with(context).load(url)
                .timeout(context.getString(R.string.mindbox_inapp_fetching_timeout).toInt())
                .listener(object :
                    RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        mindboxLogD("loading image for inapp with id $inAppId failed")
                        cancellableContinuation.resumeWithException(InAppContentFetchingError(e))
                        return true

                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        mindboxLogD("loading image for inapp with id $inAppId succeeded")
                        inAppImageSizeStorage.addSize(inAppId, url, resource.toBitmap().width, resource.toBitmap().height)
                        cancellableContinuation.resume(true)
                        return true
                    }
                }).preload()
            requests[inAppId] = target

        }

    }

    override fun cancelLoading(inAppId: String) {
        Glide.with(context).clear(requests[inAppId])
        requests.remove(inAppId)
    }
}