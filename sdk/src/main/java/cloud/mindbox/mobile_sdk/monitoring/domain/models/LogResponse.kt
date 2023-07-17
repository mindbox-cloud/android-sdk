package cloud.mindbox.mobile_sdk.monitoring.domain.models

import org.threeten.bp.ZonedDateTime

data class LogResponse(
    val zonedDateTime: ZonedDateTime,
    val log: String,
)