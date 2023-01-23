package cloud.mindbox.mobile_sdk.monitoring

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import kotlinx.coroutines.flow.first

internal class MonitoringRepositoryImpl(
    private val context: Context,
    private val monitoringDao: MonitoringDao,
    private val monitoringMapper: MonitoringMapper,
) : MonitoringRepository {

    override suspend fun saveLog(timestamp: Long, message: String) {
        monitoringDao.insertLog(monitoringMapper.mapLogInfoToMonitoringEntity(timestamp, message))
    }

    override suspend fun sendLogs(
        monitoringStatus: String,
        requestId: String,
        startTimeStamp: Long,
        endTimeStamp: Long,
    ) {
        val configuration = DbManager.listenConfigurations().first()
        GatewayManager.sendLogEvent(
            logs = monitoringMapper.mapMonitoringEntityToLogInfo(
                monitoringStatus = monitoringStatus,
                requestId = requestId,
                monitoringEntityList = monitoringDao.getLogs(
                    startInstant = startTimeStamp,
                    endInstant = endTimeStamp
                )
            ), context = context, configuration = configuration
        )
    }

}