package cloud.mindbox.mobile_sdk.monitoring.domain.models

import java.time.ZonedDateTime

internal data class LogRequest(
    val requestId: String,
    val deviceId: String,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)