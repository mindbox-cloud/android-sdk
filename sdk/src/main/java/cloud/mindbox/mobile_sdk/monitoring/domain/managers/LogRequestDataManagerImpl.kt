package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogRequestDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import java.security.MessageDigest

internal fun String.md5(): String = MessageDigest.getInstance("MD5")
    .digest(lowercase().toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal class LogRequestDataManagerImpl : LogRequestDataManager {

    override fun filterCurrentDeviceUuidLogs(logs: List<LogRequest>?): List<LogRequest> {
        if (logs.isNullOrEmpty()) return emptyList()
        val deviceUuid = MindboxPreferences.deviceUuid
        if (deviceUuid.isBlank()) return emptyList()
        val deviceUuidHash = deviceUuid.md5()
        return logs.filter { logRequest ->
            logRequest.target.equals(deviceUuidHash, ignoreCase = true)
        }
    }

    override fun checkRequestIdProcessed(requestIds: HashSet<String>, requestId: String): Boolean {
        return requestIds.contains(requestId)
    }
}
