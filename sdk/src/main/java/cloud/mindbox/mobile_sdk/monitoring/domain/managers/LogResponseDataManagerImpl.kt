package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogResponseDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import java.time.ZonedDateTime

internal class LogResponseDataManagerImpl : LogResponseDataManager {

    override fun getStatus(
        filteredLogs: List<LogResponse>,
        firstLog: LogResponse,
        lastLog: LogResponse,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): String {
        return when {
            (lastLog.zonedDateTime.isBefore(from)) -> {
                STATUS_NO_NEW_LOGS + lastLog.zonedDateTime
            }
            (firstLog.zonedDateTime.isAfter(to)) -> {
                STATUS_NO_OLD_LOGS + firstLog.zonedDateTime
            }
            filteredLogs.joinToString().length * 2 > OPERATION_LIMIT -> {
                STATUS_REQUESTED_LOG_IS_TOO_LARGE
            }
            else -> {
                STATUS_OK
            }
        }
    }

    /**
     * Each symbol takes 2 bytes so a string will approximately take 2 * length bytes in memory
     **/
    override fun getFilteredLogs(
        filteredLogs: List<LogResponse>,
        firstLog: LogResponse,
        lastLog: LogResponse,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<LogResponse> {
        if (firstLog.zonedDateTime.isAfter(to)) return emptyList()
        if (lastLog.zonedDateTime.isBefore(from)) return emptyList()
        return if (filteredLogs.joinToString().length * 2 < OPERATION_LIMIT) filteredLogs else {
            var droppingLogsCount = 1
            while (filteredLogs.dropLast(droppingLogsCount)
                    .joinToString().length * 2 > OPERATION_LIMIT
            ) {
                droppingLogsCount++
            }
            filteredLogs.dropLast(droppingLogsCount)
        }
    }

    companion object {
        const val STATUS_OK = "Ok"
        const val STATUS_NO_OLD_LOGS = "No data found. The elder log has date: "
        const val STATUS_NO_NEW_LOGS = "No data found. The latest log has date: "
        const val STATUS_REQUESTED_LOG_IS_TOO_LARGE = "The requested log size is too large."
        private const val OPERATION_LIMIT = 1024 * 800
    }
}