package cloud.mindbox.mobile_sdk.monitoring.data.repositories

import cloud.mindbox.mobile_sdk.convertToString
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.monitoring.data.checkers.LogStoringDataCheckerImpl
import cloud.mindbox.mobile_sdk.monitoring.data.mappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogStoringDataChecker
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

internal class MonitoringRepositoryImpl(
    private val monitoringDao: MonitoringDao,
    private val monitoringMapper: MonitoringMapper,
    private val gson: Gson,
    private val logStoringDataChecker: LogStoringDataChecker,
    private val monitoringValidator: MonitoringValidator,
    private val gatewayManager: GatewayManager
) : MonitoringRepository {
    override suspend fun deleteFirstLog() {
        monitoringDao.deleteFirstLog()
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
                message
            )
        )
        if (logStoringDataChecker.isDatabaseMemorySizeExceeded()) {
            LogStoringDataCheckerImpl.needCleanLog.set(true)
            try {
                Mutex().withLock {
                    if (LogStoringDataCheckerImpl.deletionIsInProgress.get().not()) {
                        LogStoringDataCheckerImpl.deletionIsInProgress.set(true)
                        monitoringDao.deleteFirstTenPercentOfLogs()

                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun getLogs(
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
    ): List<LogResponse> {
        return monitoringMapper.mapMonitoringEntityListToLogResponseList(
            monitoringDao.getLogs(startTime.convertToString(), endTime.convertToString())
                .filter { monitoringEntity ->
                    monitoringValidator.validateMonitoring(monitoringEntity)
                }
        )
    }

    override suspend fun sendLogs(
        monitoringStatus: String,
        requestId: String,
        logs: List<LogResponse>,
    ) {
        val configuration = DbManager.listenConfigurations().first()
        gatewayManager.sendLogEvent(
            logs = monitoringMapper.mapMonitoringEntityToLogInfo(
                monitoringStatus = monitoringStatus,
                requestId = requestId,
                monitoringEntityList = logs
            ),
            configuration = configuration
        )
    }

}