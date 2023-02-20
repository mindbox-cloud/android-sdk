package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoError
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationError
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl

internal class InAppChoosingManagerImpl(private val inAppFilteringManager: InAppFilteringManager) :
    InAppChoosingManager {

    override suspend fun chooseInAppToShow(inApps: List<InApp>): InAppType? {
        runCatching {
            for (inApp in inApps) {
                inApp.targeting.fetchTargetingInfo()
                if (inApp.targeting.checkTargeting()) {
                    return inApp.form.variants.firstOrNull()?.mapToInAppType(inApp.id)
                }
            }
        }.onFailure { throwable ->
            return when (throwable) {
                is GeoError -> {
                    MindboxLoggerImpl.e(this, "Error fetching geo", throwable)
                    chooseInAppToShow(inAppFilteringManager.filterGeoFreeInApps(inApps))
                }
                is SegmentationError -> {
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