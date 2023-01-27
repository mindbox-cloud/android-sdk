package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.convertToLongDateMilliSeconds
import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank

internal class MonitoringValidator {

    fun validateMonitoring(logRequest: LogRequestDtoBlank): Boolean {
        return validateRequestId(logRequest) && validateDeviceId(logRequest) && validateFrom(
            logRequest
        ) && validateTo(logRequest)
    }

    private fun validateRequestId(logRequest: LogRequestDtoBlank): Boolean {
        return logRequest.requestId.isNullOrBlank().not()
    }

    private fun validateDeviceId(logRequest: LogRequestDtoBlank): Boolean {
        return logRequest.deviceId.isNullOrBlank().not()
    }

    private fun validateFrom(logRequest: LogRequestDtoBlank): Boolean {
        return !logRequest.from.isNullOrBlank() && logRequest.from.convertToLongDateMilliSeconds() != 0L
    }

    private fun validateTo(logRequest: LogRequestDtoBlank): Boolean {
        return !logRequest.to.isNullOrBlank() && logRequest.to.convertToLongDateMilliSeconds() != 0L
    }
}