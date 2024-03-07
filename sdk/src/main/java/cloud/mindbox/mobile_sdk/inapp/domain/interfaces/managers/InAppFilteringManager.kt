package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal interface InAppFilteringManager {

    fun filterNotShownInApps(shownInApps: Set<String>, inApps: List<InApp>): List<InApp>

    fun filterOperationFreeInApps(inApps: List<InApp>): List<InApp>

    fun filterGeoFreeInApps(inApps: List<InApp>): List<InApp>

    fun filterSegmentationFreeInApps(inApps: List<InApp>): List<InApp>

    fun filterUnShownInAppsByEvent(inApps: List<InApp>, event: InAppEventType): List<InApp>

    fun filterInAppsByEvent(inApps: List<InApp>, event: InAppEventType): List<InApp>

    fun filterABTestsInApps(inApps: List<InApp>, abtestsInAppsPool: Collection<String>): List<InApp>

}