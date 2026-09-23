package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.models.TrackingId

internal sealed interface TrackingIdResult {

    data class Success(val trackingId: TrackingId) : TrackingIdResult

    data class Denied(val type: String) : TrackingIdResult

    object Unavailable : TrackingIdResult

    object NotSupported : TrackingIdResult
}
