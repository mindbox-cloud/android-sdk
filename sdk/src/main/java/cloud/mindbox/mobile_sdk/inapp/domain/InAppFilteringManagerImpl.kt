package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.firstOverlayVariant
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.PlaceKey

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

    override fun filterEmbeddedInAppsByPlace(
        inApps: List<InApp>,
        placeSystemName: PlaceKey
    ): List<InApp> {
        return inApps.filter { inApp ->
            inApp.embeddedVariants().any { variant -> variant.placeSystemName == placeSystemName }
        }
    }

    override fun filterOutNonOverlayInApps(inApps: List<InApp>): List<InApp> =
        inApps.filter { inApp ->
            (inApp.firstOverlayVariant() != null).also { hasOverlayVariant ->
                if (!hasOverlayVariant) {
                    mindboxLogI(
                        "InApp with id = ${inApp.id} has no variant an overlay can show, skipping it"
                    )
                }
            }
        }

    override fun filterOutDirectCallInApps(inApps: List<InApp>): List<InApp> =
        inApps.filter { inApp ->
            (inApp.displayConditions != DisplayConditions.DIRECT_CALL).also { keep ->
                if (!keep) {
                    mindboxLogI(
                        "InApp with id = ${inApp.id} is direct-call only, cutting it from the candidates"
                    )
                }
            }
        }

    private fun InApp.embeddedVariants(): List<InAppType.Embedded> =
        form.variants.filterIsInstance<InAppType.Embedded>()
}
