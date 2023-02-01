package cloud.mindbox.mobile_sdk.monitoring.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.convertToString
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.monitoring.data.rmappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

internal class MonitoringRepositoryImpl(
    private val context: Context,
    private val monitoringDao: MonitoringDao,
    private val monitoringMapper: MonitoringMapper,
    private val gson: Gson,
) : MonitoringRepository {
    override suspend fun deleteFirstLog() {
        monitoringDao.deleteFirstLog(monitoringDao.getFirstLog())
    }

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

    override suspend fun getFirstLog(): LogResponse {
        return monitoringMapper.mapMonitoringEntityToLogResponse(monitoringDao.getFirstLog())
    }

    override suspend fun getLastLog(): LogResponse {
        return monitoringMapper.mapMonitoringEntityToLogResponse(monitoringDao.getLastLog())
    }

    override suspend fun saveLog(zonedDateTime: ZonedDateTime, message: String) {
        monitoringDao.insertLog(
            monitoringMapper.mapLogInfoToMonitoringEntity(
                zonedDateTime.convertToString(),
                "${zonedDateTime.convertToString()} $message"
            )
        )
    }

    override suspend fun getLogs(
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): List<LogResponse> {
        return monitoringMapper.mapMonitoringEntityListToLogResponseList(
            monitoringDao.getLogs(startTime.convertToString(), endTime.convertToString())
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