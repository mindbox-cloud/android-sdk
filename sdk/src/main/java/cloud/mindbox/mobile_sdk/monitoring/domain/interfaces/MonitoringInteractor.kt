package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

internal interface MonitoringInteractor {

    suspend fun saveLog(zonedDateTime: String, message: String)
    fun processLogs()
}