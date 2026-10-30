package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.PlaceKey

internal data class EmbeddedPlaceEvent(
    val placeSystemName: PlaceKey,
    val triggerEvent: InAppEventType,
)
