package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import android.graphics.drawable.Drawable
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class InAppGlideImageLoaderImpl(
    private val context: Context
) : InAppImageLoader {

    private var target: Target<Drawable>? = null

    override suspend fun loadImage(inAppId: String, url: String): Boolean {
        mindboxLogD("loading image for inapp with id $inAppId started")
        return suspendCancellableCoroutine { cancellableContinuation ->
            Glide.with(context).load(url)
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
                        cancellableContinuation.resume(false)
                        return true

                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        mindboxLogD("loading image for inapp with id $inAppId succeeded")
                        cancellableContinuation.resume(true)
                        return true
                    }
                }).preload()

        }

    }

    override fun cancelLoading(inAppId: String) {
        Glide.with(context).clear(target)
    }
}