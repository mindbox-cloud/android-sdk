package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal class InAppFilteringManagerImpl(
    private val inAppRepository: InAppRepository
) :
    InAppFilteringManager {
    override fun filterOperationFreeInApps(inApps: List<InApp>): List<InApp> {
        return inApps.filterNot { inApp -> inApp.targeting.hasOperationNode() }
    }

    override fun filterGeoFreeInApps(inApps: List<InApp>): List<InApp> {
        return inApps.filterNot { inApp -> inApp.targeting.hasGeoNode() }
    }

    override fun filterSegmentationFreeInApps(inApps: List<InApp>): List<InApp> {
        return inApps.filterNot { inApp -> inApp.targeting.hasSegmentationNode() }
    }

    override fun filterUnShownInAppsByEvent(inApps: List<InApp>, event: InAppEventType): List<InApp> =
        if (event == InAppEventType.AppStartup) {
            inApps
        } else {
            inAppRepository.getUnShownOperationalInAppsByOperation(event.name)
        }

    override fun filterInAppsByEvent(
        inApps: List<InApp>,
        event: InAppEventType
    ): List<InApp> = if (event is InAppEventType.AppStartup) {
        inApps
    } else {
        inAppRepository.getOperationalInAppsByOperation(
            event.name
        )
    }

    override fun filterABTestsInApps(
        inApps: List<InApp>,
        abtestsInAppsPool: Collection<String>
    ): List<InApp> = inApps.filter { inApp: InApp -> abtestsInAppsPool.contains(inApp.id) }
}
