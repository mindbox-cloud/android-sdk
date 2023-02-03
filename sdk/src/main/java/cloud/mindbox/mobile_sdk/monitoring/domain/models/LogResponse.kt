package cloud.mindbox.mobile_sdk.monitoring.domain.models

import java.time.ZonedDateTime

data class LogResponse(
    val zonedDateTime: ZonedDateTime,
    val log: String,
)