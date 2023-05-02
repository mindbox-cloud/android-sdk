package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class InAppPicassoImageLoaderImpl(private val picasso: Picasso) : InAppImageLoader {


    override suspend fun loadImage(inAppId: String, url: String): Boolean {
        mindboxLogD("loading started")
        return suspendCancellableCoroutine { cancellableContinuation ->
            picasso.load(url).tag(inAppId).fetch(object : Callback {
                override fun onSuccess() {
                    mindboxLogD("image loaded from url $url")
                    cancellableContinuation.resume(true)
                }

                override fun onError(e: Exception?) {
                    mindboxLogE("image failed to load", e)
                    cancellableContinuation.resume(false)
                }

            })
        }

    }

    override fun cancelLoading(inAppId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            mindboxLogD("cancelling request for inapp with id $inAppId")
            picasso.cancelTag(inAppId)
        }
    }
}