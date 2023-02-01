package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

import java.time.ZonedDateTime

internal interface MonitoringInteractor {

    suspend fun saveLog(zonedDateTime: ZonedDateTime, message: String)
    fun processLogs()
}