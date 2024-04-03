package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*

internal class SessionStorageManager {

    var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var unShownOperationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    var operationalInApps: HashMap<String, MutableList<InApp>> = hashMapOf()
    var isInAppMessageShown: Boolean = false
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var productSegmentationFetchStatus: ProductSegmentationFetchStatus =
        ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var inAppProductSegmentations: HashMap<String, Set<ProductSegmentationResponseWrapper>> =
        HashMap()
    var currentSessionInApps: List<InApp> = mutableListOf()
    var shownInAppIdsWithEvents = mutableMapOf<String, MutableSet<Int>>()
    var shouldCheckInAppTtl: Boolean = false

}