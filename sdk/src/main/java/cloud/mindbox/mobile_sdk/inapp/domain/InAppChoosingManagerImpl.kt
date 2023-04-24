package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.*

internal class InAppChoosingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppContentFetcher: InAppContentFetcher,
) :
    InAppChoosingManager {

    override suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
    ): InAppType? {
        for (inApp in inApps) {
            val data = getTargetingData(triggerEvent)
            var isInAppContentFetched = false
            runCatching {
                withContext(Job() + Dispatchers.IO) {
                    listOf(launch {
                        inApp.targeting.fetchTargetingInfo(data)
                    }, launch {
                        isInAppContentFetched =
                            inAppContentFetcher.fetchContent(inApp.form.variants.first())
                    }).joinAll()
                }
            }.onFailure { throwable ->
                return when (throwable) {
                    is GeoError -> {
                        inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                        MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                        chooseInAppToShow(inApps, triggerEvent)
                    }
                    is CustomerSegmentationError -> {
                        inAppSegmentationRepository.setCustomerSegmentationStatus(
                            CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                        )
                        MindboxLoggerImpl.e(this,
                            "Error fetching customer segmentations",
                            throwable)
                        chooseInAppToShow(inApps, triggerEvent)
                    }
                    else -> {
                        MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                        throw throwable
                    }
                }
            }
            if (!isInAppContentFetched) continue
            val check = inApp.targeting.checkTargeting(data)
            mindboxLogD("Check ${inApp.targeting.type}: $check")
            if (check) {
                return inApp.form.variants.firstOrNull()?.mapToInAppType(inApp.id)
            }
        }
        return null
    }

    private fun getTargetingData(triggerEvent: InAppEventType): TargetingData {
        val ordinalEvent = triggerEvent as? InAppEventType.OrdinalEvent

        return TargetingDataWrapper(
            triggerEvent.name,
            ordinalEvent?.body
        )
    }

    private class TargetingDataWrapper(
        override val triggerEventName: String,
        override val operationBody: String? = null,
    ) : TargetingData.OperationName, TargetingData.OperationBody

}