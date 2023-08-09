package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class InAppContentFetcherImpl(
    private val inAppImageLoader: InAppImageLoader,
) : InAppContentFetcher {

    private val inAppImageStorage: MutableList<Deferred<Boolean>> = mutableListOf()

    override suspend fun fetchContent(inAppId: String, formVariant: InAppType): Boolean {
        when (formVariant) {
            is InAppType.ModalWindow -> {
                formVariant.layers.filterIsInstance<InAppType.ModalWindow.Layer.ImageLayer>()
                    .forEach { layer ->
                        when (layer.source) {
                            is InAppType.ModalWindow.Layer.ImageLayer.Source.UrlSource -> {
                                withContext(Dispatchers.IO) {
                                    inAppImageStorage.add(async {
                                        inAppImageLoader.loadImage(inAppId, layer.source.url)
                                    })
                                }
                            }
                        }
                    }
                if (inAppImageStorage.map { deferredResult -> deferredResult.await() }
                        .contains(false)) return false
            }
        }
        return true
    }

    override fun cancelFetching(inAppId: String) {
        inAppImageLoader.cancelLoading(inAppId)
    }
}