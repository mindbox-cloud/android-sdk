package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationError
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoError
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingData
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class InAppChoosingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppContentFetcher: InAppContentFetcher,
    private val inAppRepository: InAppRepository
) :
    InAppChoosingManager {

    override suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
    ): InAppType? {
        for (inApp in inApps) {
            val data = getTargetingData(triggerEvent)
            var isTargetingErrorOccurred = false
            var isInAppContentFetched = false
            coroutineScope {
                joinAll(
                    launch {
                        isInAppContentFetched =
                            withTimeoutOrNull(inAppRepository.getInAppContentTimeout()) {
                                inAppContentFetcher.fetchContent(inApp.form.variants.first())
                            } ?: false
                    },
                    launch {
                        runCatching {
                            inApp.targeting.fetchTargetingInfo(data)
                        }.onFailure { throwable ->
                            when (throwable) {
                                is GeoError -> {
                                    isTargetingErrorOccurred = true
                                    inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                                    MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                                }

                                is CustomerSegmentationError -> {
                                    isTargetingErrorOccurred = true
                                    inAppSegmentationRepository.setCustomerSegmentationStatus(
                                        CustomerSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
                                    )
                                    MindboxLoggerImpl.e(
                                        this,
                                        "Error fetching customer segmentations",
                                        throwable
                                    )
                                }

                                else -> {
                                    MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                                    throw throwable
                                }
                            }
                        }
                    })
            }
            mindboxLogD("loading and targeting fetching finished")
            if (isTargetingErrorOccurred) return chooseInAppToShow(inApps, triggerEvent)
            if (!isInAppContentFetched) {
                mindboxLogD("Skipping inApp with id = ${inApp.id} due to content fetching error.")
                continue
            }
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