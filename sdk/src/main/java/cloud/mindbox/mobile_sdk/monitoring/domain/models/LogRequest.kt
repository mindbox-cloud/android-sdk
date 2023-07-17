package cloud.mindbox.mobile_sdk.monitoring.domain.models

import org.threeten.bp.ZonedDateTime

internal data class LogRequest(
    val requestId: String,
    val deviceId: String,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)