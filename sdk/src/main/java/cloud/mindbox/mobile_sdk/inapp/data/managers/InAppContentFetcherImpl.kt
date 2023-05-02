package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.models.Payload

internal class InAppContentFetcherImpl(
    private val inAppImageLoader: InAppImageLoader,
) : InAppContentFetcher {

    override suspend fun fetchContent(inAppId: String, formVariant: Payload): Boolean {
        when (formVariant) {
            is Payload.SimpleImage -> {
                return inAppImageLoader.loadImage(inAppId, formVariant.imageUrl)
            }
        }
    }

    override fun cancelFetching(inAppId: String) {
        inAppImageLoader.cancelLoading(inAppId)
    }
}