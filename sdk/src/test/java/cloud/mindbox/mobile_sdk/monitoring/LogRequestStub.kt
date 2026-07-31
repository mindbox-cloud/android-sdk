package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.monitoring.domain.models.Md5Hash
import org.threeten.bp.ZonedDateTime

internal class LogRequestStub {
    companion object {
        fun getLogRequest(): LogRequest = LogRequest(
            requestId = "",
            target = Md5Hash.ofHex(""),
            from = ZonedDateTime.now(),
            to = ZonedDateTime.now()
        )

        fun getLogRequestDtoBlank(): LogRequestDtoBlank = LogRequestDtoBlank(
            requestId = "",
            target = "",
            from = "",
            to = ""
        )
    }
}
