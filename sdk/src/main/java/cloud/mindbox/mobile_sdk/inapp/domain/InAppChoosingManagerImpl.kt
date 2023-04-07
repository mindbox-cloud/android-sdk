package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal class InAppChoosingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository
) :
    InAppChoosingManager {

    override suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
    ): InAppType? {
        for (inApp in inApps) {
            runCatching {
                val data = getTargetingData(triggerEvent)
                inApp.targeting.fetchTargetingInfo(getTargetingData(triggerEvent))
                val check = inApp.targeting.checkTargeting(data)
                mindboxLogD("Check ${inApp.targeting.type}: $check")
                if (check) {
                    return inApp.form.variants.firstOrNull()?.mapToInAppType(inApp.id)
                }
            }.onFailure { throwable ->
                when (throwable) {
                    is GeoError -> {
                        inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                        MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                    }
                    is SegmentationError -> {
                        inAppSegmentationRepository.setCustomerSegmentationStatus(
                            SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                        )
                        MindboxLoggerImpl.e(this, "Error fetching segmentations", throwable)
                    }
                    else -> {
                        MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                        throw throwable
                    }
                }
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