package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

class InAppCoilImageLoaderImpl(private val context: Context) : InAppImageLoader {

    override suspend fun loadImage(url: String): Boolean {
        val request = ImageRequest.Builder(context).data(url).build()
        val imageResult = context.imageLoader.execute(request)
        return imageResult is SuccessResult
    }
}