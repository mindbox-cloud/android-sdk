package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank

internal class LogRequestStub {
    companion object {
        fun getLogRequest(): LogRequest = LogRequest(
            requestId = "",
            deviceId = "",
            from = "",
            to = ""
        )

        fun getLogRequestDtoBlank(): LogRequestDtoBlank = LogRequestDtoBlank(
            requestId = null,
            deviceId = null,
            from = null,
            to = null
        )
    }
}