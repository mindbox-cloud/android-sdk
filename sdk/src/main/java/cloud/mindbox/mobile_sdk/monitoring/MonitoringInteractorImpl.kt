package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.monitoring.data.rmappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogRequestDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogResponseDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class MonitoringInteractorImpl(
    private val inAppRepository: InAppRepository,
    private val monitoringRepository: MonitoringRepository,
    private val logResponseDataManager: LogResponseDataManager,
    private val logRequestDataManager: LogRequestDataManager,
) :
    MonitoringInteractor {
    override fun processLogs() {
        MindboxLoggerImpl.monitoringScope.launch {
            val firstLog = monitoringRepository.getFirstLog()
            val lastLog = monitoringRepository.getLastLog()
            inAppRepository.listenInAppConfig().collect { config ->
                logRequestDataManager.filterCurrentDeviceUuidLogs(config?.monitoring)
                    .filterNot { logRequest ->
                        logRequestDataManager.checkRequestIdProcessed(
                            monitoringRepository.getRequestIds(),
                            logRequest.requestId
                        )
                    }.onEach { logRequest ->
                        monitoringRepository.saveRequestId(logRequest.requestId)
                    }.map { logRequest ->
                        val logs = monitoringRepository.getLogs(logRequest.from, logRequest.to)
                        async {
                            monitoringRepository.sendLogs(
                                monitoringStatus = logResponseDataManager.getStatus(
                                    filteredLogs = logs, firstLog = firstLog, lastLog = lastLog,
                                    from = logRequest.from,
                                    to = logRequest.to
                                ), logRequest.requestId, logResponseDataManager.getFilteredLogs(
                                    filteredLogs = logs,
                                    firstLog = firstLog,
                                    lastLog = lastLog,
                                    from = logRequest.from, to = logRequest.to
                                )
                            )
                        }
                    }.awaitAll()
            }
        }
    }
}