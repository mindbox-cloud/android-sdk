package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.SessionManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus

internal class SessionManagerImpl : SessionManager {

    override var inAppSegmentations: SegmentationCheckInApp? = null
    override var operationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    override var isInAppMessageShown: Boolean = false
    override var segmentationFetchStatus: SegmentationFetchStatus =
        SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    override var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED

}