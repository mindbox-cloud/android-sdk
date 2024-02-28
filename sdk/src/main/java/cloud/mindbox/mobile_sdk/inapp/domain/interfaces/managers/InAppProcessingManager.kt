package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal interface InAppProcessingManager {

    suspend fun chooseInAppToShow(inApps: List<InApp>, triggerEvent: InAppEventType): InAppType?

    suspend fun sendTargetedInApps(inApps: List<InApp>, triggerEvent: InAppEventType)
}