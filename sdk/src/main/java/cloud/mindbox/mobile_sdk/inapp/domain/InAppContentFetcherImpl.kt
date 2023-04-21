package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.models.Payload

internal class InAppContentFetcherImpl(
    private val inAppImageLoader: InAppImageLoader,
) : InAppContentFetcher {

    override suspend fun fetchContent(formVariant: Payload): Boolean {
        when (formVariant) {
            is Payload.SimpleImage -> {
                return inAppImageLoader.loadImage(formVariant.imageUrl)
            }
        }
    }
}