package cloud.mindbox.mobile_sdk.monitoring

internal interface MonitoringRepository {

    suspend fun saveLog(timestamp: Long, message: String)

    suspend fun sendLogs(monitoringStatus: String, requestId: String, startTimeStamp: Long, endTimeStamp: Long)
}