package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import org.threeten.bp.ZonedDateTime

internal class LogResponseStub {
    companion object {
        fun get() = LogResponse(zonedDateTime = ZonedDateTime.now(), log = "")
    }
}
