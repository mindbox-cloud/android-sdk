package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.*

internal class SessionStorageManager {

    var inAppCustomerSegmentations: SegmentationCheckWrapper? = null
    var operationalInApps: HashMap<String, MutableList<InApp>> = HashMap()
    var isInAppMessageShown: Boolean = false
    var customerSegmentationFetchStatus: CustomerSegmentationFetchStatus =
        CustomerSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var geoFetchStatus: GeoFetchStatus = GeoFetchStatus.GEO_NOT_FETCHED
    var productSegmentationFetchStatus: ProductSegmentationFetchStatus =
        ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED
    var inAppProductSegmentations: HashMap<String, Set<ProductSegmentationResponseWrapper>> =
        HashMap()
    var inApps: List<InApp>  = mutableListOf()
    var currentShownInAppId = ""

}