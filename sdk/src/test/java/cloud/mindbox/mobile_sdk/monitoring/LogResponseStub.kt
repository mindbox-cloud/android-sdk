package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse

internal class LogResponseStub {
    companion object {
        fun get() = LogResponse(time = "", log = "")
    }
}