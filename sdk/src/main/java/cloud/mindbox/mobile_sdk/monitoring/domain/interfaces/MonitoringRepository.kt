package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import java.time.ZonedDateTime

internal interface MonitoringRepository {

    suspend fun deleteFirstLog()

    fun getRequestIds(): HashSet<String>
    fun saveRequestId(id: String)

    suspend fun getFirstLog(): LogResponse

    suspend fun getLastLog(): LogResponse

    suspend fun getLogs(startTime: ZonedDateTime, endTime: ZonedDateTime): List<LogResponse>
    suspend fun sendLogs(
        monitoringStatus: String,
        requestId: String,
        logs: List<LogResponse>,
    )
    suspend fun saveLog(zonedDateTime: ZonedDateTime, message: String)
}