package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus

internal class SessionStorageManager {

    var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var operationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    var isInAppMessageShown: Boolean = false
    var segmentationFetchStatus: SegmentationFetchStatus =
        SegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var inAppProductSegmentations: HashMap<String, ProductSegmentationResponseWrapper> = HashMap()

}