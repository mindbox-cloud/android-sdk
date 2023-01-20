package cloud.mindbox.mobile_sdk.monitoring

class MonitoringManager(
    private val monitoringDao: MonitoringDao,
    private val monitoringMapper: MonitoringMapper,
) {

    suspend fun saveLog(timestamp: Long, message: String) {
        monitoringDao.insertLog(monitoringMapper.mapLogInfoToMonitoringEntity(timestamp, message))
    }

    suspend fun getLogs(startTimeStamp: Long, endTimeStamp: Long) {
        monitoringDao.getLogs(startTimeStamp,
            endTimeStamp).map { monitoringEntity ->
            monitoringMapper.mapMonitoringEntityToLogInfo(monitoringEntity)
        }
    }

}