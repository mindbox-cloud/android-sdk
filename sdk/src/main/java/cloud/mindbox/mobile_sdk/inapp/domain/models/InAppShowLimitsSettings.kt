package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.Milliseconds

internal data class InAppShowLimitsSettings(
    val maxInappsPerSession: Int? = null,
    val maxInappsPerDay: Int? = null,
    val minIntervalBetweenShows: Milliseconds? = null
)
