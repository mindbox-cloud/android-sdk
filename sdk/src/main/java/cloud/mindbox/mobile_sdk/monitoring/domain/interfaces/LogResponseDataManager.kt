package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import org.threeten.bp.ZonedDateTime

internal interface LogResponseDataManager {

    fun getStatus(
        filteredLogs: List<LogResponse>,
        firstLog: LogResponse,
        lastLog: LogResponse,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): String

    fun getFilteredLogs(
        filteredLogs: List<LogResponse>, firstLog: LogResponse,
        lastLog: LogResponse, from: ZonedDateTime, to: ZonedDateTime,
    ): List<LogResponse>
}