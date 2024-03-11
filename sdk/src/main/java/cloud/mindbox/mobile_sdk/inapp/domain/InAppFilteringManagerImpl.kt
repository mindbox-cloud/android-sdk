package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.hasImageLayerWithRedirectUrlAction
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal class InAppFilteringManagerImpl(
    private val inAppRepository: InAppRepository,
    private val mindboxNotificationManager: MindboxNotificationManager
) :
    InAppFilteringManager {

    override fun filterNotShownInApps(shownInApps: Set<String>, inApps: List<InApp>): List<InApp> {
        MindboxLoggerImpl.d(
            this,
            "Already shown innaps: $shownInApps"
        )
        return inApps.filterNot { inApp -> shownInApps.contains(inApp.id) }
    }

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
    ): List<InApp> {
        return if (event is InAppEventType.AppStartup) inApps else inAppRepository.getOperationalInAppsByOperation(
            event.name
        )
    }

    override fun filterABTestsInApps(
        inApps: List<InApp>,
        abtestsInAppsPool: Collection<String>
    ): List<InApp> = inApps.filter { inApp: InApp -> abtestsInAppsPool.contains(inApp.id) }

    override fun filterPushInAppsByPermissionStatus(inApps: List<InApp>): List<InApp> =
        if (!mindboxNotificationManager.isNotificationEnabled()) {
            mindboxLogI("Notification doesn't enabled")
            inApps
        } else {
            mindboxLogI("Notification already enabled")
            inApps.filter { inApp ->
                inApp.form.variants.any { it.hasImageLayerWithRedirectUrlAction() }
            }
        }
}