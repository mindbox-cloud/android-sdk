package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus

internal interface SessionManager {

    var inAppSegmentations: SegmentationCheckInApp?

    var operationalInApps: HashMap<String, MutableList<InApp>>

    var isInAppMessageShown: Boolean

    var segmentationFetchStatus: SegmentationFetchStatus

    var geoFetchStatus: GeoFetchStatus


}
