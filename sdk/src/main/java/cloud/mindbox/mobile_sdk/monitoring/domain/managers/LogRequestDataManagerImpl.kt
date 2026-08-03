package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogRequestDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.monitoring.domain.models.Md5Hash
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

internal class LogRequestDataManagerImpl : LogRequestDataManager {

    override fun filterCurrentDeviceUuidLogs(logs: List<LogRequest>?): List<LogRequest> {
        if (logs.isNullOrEmpty()) return emptyList()
        val deviceUuid = MindboxPreferences.deviceUuid
        if (deviceUuid.isBlank()) return emptyList()
        val deviceUuidHash = Md5Hash.ofDeviceUuid(deviceUuid)
        return logs.filter { logRequest ->
            logRequest.target == deviceUuidHash
        }
    }

    override fun checkRequestIdProcessed(requestIds: HashSet<String>, requestId: String): Boolean {
        return requestIds.contains(requestId)
    }
}
