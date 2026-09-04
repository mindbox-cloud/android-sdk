package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.Timestamp

internal data class ShowReservation(
    val owner: String,
    val inAppId: String,
    val reservedAt: Timestamp,
)
