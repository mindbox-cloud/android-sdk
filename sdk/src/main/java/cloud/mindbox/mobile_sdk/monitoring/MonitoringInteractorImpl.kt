package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.convertToLongDateSeconds
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class MonitoringInteractorImpl(
    private val inAppRepository: InAppRepository,
    private val monitoringRepository: MonitoringRepository,
) :
    MonitoringInteractor {
    override fun processLogs() {
        MindboxLoggerImpl.monitoringScope.launch {
            inAppRepository.listenInAppConfig().collect { config ->
                filterCurrentDeviceUuidLogs(config?.monitoring).map { logRequest ->
                    async {
                        monitoringRepository.sendLogs(
                            monitoringStatus = getStatus(logRequest.from, logRequest.to),
                            requestId = logRequest.requestId,
                            startTimeStamp = logRequest.from.convertToLongDateSeconds(),
                            endTimeStamp = logRequest.to.convertToLongDateSeconds()
                        )
                    }
                }.awaitAll()
            }
        }
    }

    private fun getStatus(from: String, to: String): String {
        when {
            3==4 == -> {
                STATUS_REQUESTED_LOG_IS_TOO_LARGE
            }
            from. -> {
                STATUS_NO_NEW_LOGS +
            }
            -> {
                STATUS_NO_OLD_LOGS + ""
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
        private const val STATUS_OK = "Ok"
        private const val STATUS_NO_OLD_LOGS = "The elder log has date:"
        private const val STATUS_NO_NEW_LOGS = "The latest log has date:"
        private const val STATUS_REQUESTED_LOG_IS_TOO_LARGE = "The requested log size is too large."
    }
}