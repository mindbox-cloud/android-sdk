package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.firstOverlayVariant
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
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

    override fun filterEmbeddedInAppsByPlace(
        inApps: List<InApp>,
        placeSystemName: String
    ): List<InApp> {
        return inApps.filter { inApp ->
            inApp.embeddedVariants().any { variant ->
                val matches = variant.placeSystemName == placeSystemName
                if (!matches && variant.placeSystemName.equals(placeSystemName, ignoreCase = true)) {
                    mindboxLogW(
                        "Place names differ only in letter case: config has " +
                            "'${variant.placeSystemName}', the block asked for '$placeSystemName'. " +
                            "The comparison is case-sensitive, the candidate is skipped"
                    )
                }
                matches
            }
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
        inApps.filterNot { inApp -> inApp.displayConditions == DisplayConditions.DIRECT_CALL }

    private fun InApp.embeddedVariants(): List<InAppType.Embedded> =
        form.variants.filterIsInstance<InAppType.Embedded>()
}
