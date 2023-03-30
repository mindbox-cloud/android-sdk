package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal class InAppChoosingManagerImpl(
    private val inAppGeoRepository: InAppGeoRepository,
    private val inAppSegmentationRepository: InAppSegmentationRepository,
    private val inAppFilteringManager: InAppFilteringManager,
) :
    InAppChoosingManager {

    override suspend fun chooseInAppToShow(inApps: List<InApp>): InAppType? {
        runCatching {
            for (inApp in inApps) {
                inApp.targeting.fetchTargetingInfo()
                val check = inApp.targeting.checkTargeting()
                mindboxLogD("Check ${inApp.targeting.type}: $check")
                if (check) {
                    return inApp.form.variants.firstOrNull()?.mapToInAppType(inApp.id)
                }
            }
        }.onFailure { throwable ->
            return when (throwable) {
                is GeoError -> {
                    inAppGeoRepository.setGeoStatus(GeoFetchStatus.GEO_FETCH_ERROR)
                    MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                    chooseInAppToShow(inAppFilteringManager.filterGeoFreeInApps(inApps))
                }
                is SegmentationError -> {
                    inAppSegmentationRepository.setSegmentationStatus(SegmentationFetchStatus.SEGMENTATION_FETCH_ERROR)
                    MindboxLoggerImpl.e(this, "Error fetching segmentations", throwable)
                    chooseInAppToShow(inAppFilteringManager.filterSegmentationFreeInApps(inApps))
                }
                else -> {
                    MindboxLoggerImpl.e(this, throwable.message ?: "", throwable)
                    throw throwable
                }
            }
        }
        return null
    }
}