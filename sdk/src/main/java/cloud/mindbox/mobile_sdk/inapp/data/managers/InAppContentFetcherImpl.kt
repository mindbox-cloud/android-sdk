package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class InAppContentFetcherImpl(
    private val inAppImageLoader: InAppImageLoader,
) : InAppContentFetcher {

    override suspend fun fetchContent(inAppId: String, formVariant: InAppType): Boolean {
        val inAppImageStorage: MutableList<Deferred<Boolean>> = mutableListOf()
        when (formVariant) {
            is InAppType.ModalWindow -> {
                formVariant.layers.filterIsInstance<Layer.ImageLayer>()
                    .forEach { layer ->
                        when (layer.source) {
                            is Layer.ImageLayer.Source.UrlSource -> {
                                withContext(Dispatchers.IO) {
                                    inAppImageStorage.add(async {
                                        inAppImageLoader.loadImage(inAppId, layer.source.url)
                                    })
                                }
                            }
                        }
                    }
                if (inAppImageStorage
                        .map { deferredResult -> deferredResult.await() }
                        .contains(false)) {
                    return false
                }
            }

            is InAppType.Snackbar -> {
                formVariant.layers
                    .filterIsInstance<Layer.ImageLayer>()
                    .forEach { layer ->
                        when (layer.source) {
                            is Layer.ImageLayer.Source.UrlSource -> {
                                withContext(Dispatchers.IO) {
                                    inAppImageStorage.add(async {
                                        inAppImageLoader.loadImage(inAppId, layer.source.url)
                                    })
                                }
                            }
                        }
                    }
                if (inAppImageStorage
                        .map { deferredResult -> deferredResult.await() }
                        .contains(false)) {
                    return false
                }
            }
        }
        return true
    }

    override fun cancelFetching(inAppId: String) {
        inAppImageLoader.cancelLoading(inAppId)
    }
}
