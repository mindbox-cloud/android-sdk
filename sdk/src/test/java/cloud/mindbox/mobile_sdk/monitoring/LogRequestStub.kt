package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import java.time.ZonedDateTime

internal class LogRequestStub {
    companion object {
        fun getLogRequest(): LogRequest = LogRequest(
            requestId = "",
            deviceId = "",
            from = ZonedDateTime.now(),
            to = ZonedDateTime.now()
        )

        fun getLogRequestDtoBlank(): LogRequestDtoBlank = LogRequestDtoBlank(
            requestId = null,
            deviceId = null,
            from = null,
            to = null
        )
    }
}