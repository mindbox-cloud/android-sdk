package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppPicassoImageLoaderImpl : InAppImageLoader {

    override suspend fun loadImage(url: String): Boolean {
        return suspendCoroutine { continuation ->
            Picasso.get().load(url).fetch(
                object : Callback {
                    override fun onSuccess() {
                        continuation.resume(true)
                    }

                    override fun onError(e: Exception) {
                        mindboxLogE(e.message ?: "", e)
                        continuation.resume(false)
                    }
                })
        }
    }
}