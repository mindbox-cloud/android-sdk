package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp

internal interface InAppFilteringManager {

    fun filterNotShownInApps(shownInApps: Set<String>, inApps: List<InApp>): List<InApp>

    fun filterUnOperationalInApps(inApps: List<InApp>): List<InApp>

    fun filterGeoFreeInApps(inApps: List<InApp>): List<InApp>

    fun filterSegmentationFreeInApps(inApps: List<InApp>): List<InApp>



}