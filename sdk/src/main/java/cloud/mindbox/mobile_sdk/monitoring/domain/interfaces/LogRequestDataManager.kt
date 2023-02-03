package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest

internal interface LogRequestDataManager {

    fun filterCurrentDeviceUuidLogs(logs: List<LogRequest>?): List<LogRequest>

    fun checkRequestIdProcessed(requestIds: HashSet<String>, requestId: String): Boolean
}