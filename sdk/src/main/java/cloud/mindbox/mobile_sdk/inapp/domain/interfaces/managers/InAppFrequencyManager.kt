package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp

internal interface InAppFrequencyManager {

    fun filterInAppsFrequency(inApps: List<InApp>): List<InApp>
}
