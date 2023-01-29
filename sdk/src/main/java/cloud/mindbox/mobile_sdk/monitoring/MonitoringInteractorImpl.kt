package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.convertToLongDateMilliSeconds
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.subListInclusive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

internal class MonitoringInteractorImpl(
    private val inAppRepository: InAppRepository,
    private val monitoringRepository: MonitoringRepository,
) :
    MonitoringInteractor {
    override fun processLogs() {
        MindboxLoggerImpl.monitoringScope.launch {
            inAppRepository.listenInAppConfig().collect { config ->
                filterCurrentDeviceUuidLogs(config?.monitoring).filterNot { logRequest ->
                    monitoringRepository.getRequestIds().contains(logRequest.requestId)
                }.onEach { logRequest ->
                    monitoringRepository.saveRequestId(logRequest.requestId)
                }.map { logRequest ->
                    async {
                        monitoringRepository.sendLogs(
                            monitoringStatus = getStatus(logRequest),
                            requestId = logRequest.requestId,
                            logs = filterSendingLogs(logRequest)
                        )
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Each symbol takes 2 bytes so a string will approximately take 2 * length bytes in memory
     **/
    private suspend fun filterSendingLogs(
        logRequest: LogRequest,
    ): List<LogResponse> {
        val logs = getLogs()
        if (logs.isEmpty()) return emptyList()
        if (logs.first().time.convertToLongDateMilliSeconds() > logRequest.to.convertToLongDateMilliSeconds()) return emptyList()
        if (logs.last().time.convertToLongDateMilliSeconds() < logRequest.from.convertToLongDateMilliSeconds()) return emptyList()
        return if (logs.subListInclusive(
                fromIndex = logs.indexOf(logs.find { logResponse -> logResponse.time.convertToLongDateMilliSeconds() > logRequest.from.convertToLongDateMilliSeconds() }),
                toIndex = logs.indexOf(logs.findLast { logResponse -> logResponse.time.convertToLongDateMilliSeconds() < logRequest.to.convertToLongDateMilliSeconds() })
            ).joinToString().length * 2 < OPERATION_LIMIT
        ) logs else {
            var logsInterval = logs.subListInclusive(
                fromIndex = logs.indexOf(logs.find { logResponse -> logResponse.time.convertToLongDateMilliSeconds() > logRequest.from.convertToLongDateMilliSeconds() }),
                toIndex = logs.indexOf(logs.findLast { logResponse -> logResponse.time.convertToLongDateMilliSeconds() < logRequest.to.convertToLongDateMilliSeconds() })
            )
            while (logsInterval.joinToString().length * 2 > OPERATION_LIMIT) {
                logsInterval = logsInterval.drop(1)
            }
            logsInterval
        }

    }

    private suspend fun getLogs(): List<LogResponse> {
        return monitoringRepository.getLogs()
    }

    private suspend fun getStatus(logRequest: LogRequest): String {
        val logs = getLogs()
        if (logs.isEmpty()) return STATUS_NO_LOGS
        return when {
            (logs.last().time.convertToLongDateMilliSeconds() < logRequest.from.convertToLongDateMilliSeconds()) -> {
                STATUS_NO_NEW_LOGS + logs.last().time
            }
            (logs.first().time.convertToLongDateMilliSeconds() > logRequest.to.convertToLongDateMilliSeconds()) -> {
                STATUS_NO_OLD_LOGS +  logs.first().time
            }
            logs.subListInclusive(
                fromIndex = logs.indexOf(logs.find { logResponse -> logResponse.time.convertToLongDateMilliSeconds() > logRequest.from.convertToLongDateMilliSeconds() }),
                toIndex = logs.indexOf(logs.findLast { logResponse -> logResponse.time.convertToLongDateMilliSeconds() < logRequest.to.convertToLongDateMilliSeconds() })
            ).joinToString().length * 2 > OPERATION_LIMIT -> {
                STATUS_REQUESTED_LOG_IS_TOO_LARGE
            }
            else -> {
                STATUS_OK
            }
        }
    }

    private fun filterCurrentDeviceUuidLogs(logs: List<LogRequest>?): List<LogRequest> {
        if (logs.isNullOrEmpty()) return emptyList()
        return logs.filter { logRequest ->
            logRequest.deviceId == MindboxPreferences.deviceUuid
        }
    }

    companion object {
        const val STATUS_OK = "Ok"
        const val STATUS_NO_OLD_LOGS = "No data found. The elder log has date: "
        const val STATUS_NO_NEW_LOGS = "No data found. The latest log has date: "
        const val STATUS_REQUESTED_LOG_IS_TOO_LARGE = "The requested log size is too large."
        const val STATUS_NO_LOGS = "No data found"
        private const val OPERATION_LIMIT = 1024 * 800
    }
}