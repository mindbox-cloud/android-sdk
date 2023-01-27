package cloud.mindbox.mobile_sdk.monitoring

import android.content.Context
import cloud.mindbox.mobile_sdk.convertToStringDate
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

internal class MonitoringRepositoryImpl(
    private val context: Context,
    private val monitoringDao: MonitoringDao,
    private val monitoringMapper: MonitoringMapper,
    private val gson: Gson,
) : MonitoringRepository {
    override fun getRequestIds(): HashSet<String> {
        return LoggingExceptionHandler.runCatching(HashSet()) {
            if (MindboxPreferences.logsRequestIds.isBlank()) {
                HashSet()
            } else {
                gson.fromJson(
                    MindboxPreferences.logsRequestIds,
                    object : TypeToken<HashSet<String>>() {}.type
                ) ?: HashSet()
            }
        }
    }

    override fun saveRequestId(id: String) {
        val logRequestIds = getRequestIds().apply {
            add(id)
        }
        MindboxPreferences.logsRequestIds =
            gson.toJson(logRequestIds, object : TypeToken<HashSet<String>>() {}.type)
    }

    override suspend fun saveLog(timestamp: Long, message: String) {
        monitoringDao.insertLog(
            monitoringMapper.mapLogInfoToMonitoringEntity(
                timestamp,
                timestamp.convertToStringDate() + " " + message
            )
        )
    }

    override suspend fun getLogs(): List<LogResponse> {
        return monitoringMapper.mapMonitoringEntityListToLogResponseList(
            monitoringDao.getLogs()
        )
    }

    override suspend fun sendLogs(
        monitoringStatus: String,
        requestId: String,
        logs: List<LogResponse>,
    ) {
        val configuration = DbManager.listenConfigurations().first()
        GatewayManager.sendLogEvent(
            logs = monitoringMapper.mapMonitoringEntityToLogInfo(
                monitoringStatus = monitoringStatus,
                requestId = requestId,
                monitoringEntityList = logs
            ), context = context, configuration = configuration
        )
    }

}