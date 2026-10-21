package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal interface InAppProcessingManager {

    suspend fun chooseInAppToShow(
        inApps: List<InApp>,
        triggerEvent: InAppEventType,
        selectVariant: (InApp) -> InAppType? = { inApp -> inApp.form.variants.firstOrNull() },
    ): InApp?

    suspend fun sendTargetedInApp(inApp: InApp, triggerEvent: InAppEventType)

    fun sendTargetedInApp(inApp: InApp)

    suspend fun matchesTargeting(inApp: InApp, triggerEvent: InAppEventType): Boolean

    suspend fun matchesRequestedTargeting(inApp: InApp): Boolean

    suspend fun prefetchTargetingDependencies(inApps: List<InApp>)
}
