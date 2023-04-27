package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppPicassoImageLoaderImpl(private val picasso: Picasso) : InAppImageLoader {
    override suspend fun loadImage(url: String): Boolean {
        mindboxLogD("loading started")
        return suspendCancellableCoroutine { continuation ->
            picasso.load(url).fetch(
                object : Callback {
                    override fun onSuccess() {
                        mindboxLogD("image loaded")
                        continuation.resume(true)
                    }

                    override fun onError(e: Exception) {
                        mindboxLogE("image failed to load", e)
                        continuation.resume(false)
                    }
                })
        }
    }
}