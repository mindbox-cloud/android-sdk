package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType

internal interface InAppChoosingManager {

    suspend fun chooseInAppToShow(inApps: List<InApp>): InAppType?
}