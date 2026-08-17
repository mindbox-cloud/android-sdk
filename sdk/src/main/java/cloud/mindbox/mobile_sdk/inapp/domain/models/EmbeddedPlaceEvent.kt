package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.InAppEventType

internal data class EmbeddedPlaceEvent(
    val placeSystemName: String,
    val triggerEvent: InAppEventType,
)
