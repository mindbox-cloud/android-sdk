package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import java.time.ZonedDateTime

internal class LogResponseStub {
    companion object {
        fun get() = LogResponse(zonedDateTime = ZonedDateTime.now(), log = "")
    }
}