package cloud.mindbox.mobile_sdk.monitoring.data.validators

import cloud.mindbox.mobile_sdk.convertToZonedDateTime
import cloud.mindbox.mobile_sdk.convertToZonedDateTimeWithZ
import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

internal class MonitoringValidator {

    fun validateLogRequestDtoBlank(logRequest: LogRequestDtoBlank): Boolean {
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
        val errorRez = LocalDateTime.parse(
            "1970-01-01T00:00:00",
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        )
            .atZone(
                ZoneOffset.UTC
            )
        return !logRequest.from.isNullOrBlank() && logRequest.from.convertToZonedDateTime() != errorRez
    }

    private fun validateTo(logRequest: LogRequestDtoBlank): Boolean {
        val errorRez = LocalDateTime.parse(
            "1970-01-01T00:00:00",
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        )
            .atZone(
                ZoneOffset.UTC
            )
        return !logRequest.to.isNullOrBlank() && logRequest.to.convertToZonedDateTime() != errorRez
    }

    fun validateMonitoring(monitoringEntity: MonitoringEntity): Boolean {
        val errorRez = LocalDateTime.parse(
            "1970-01-01T00:00:00",
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        ).atZone(
            ZoneOffset.UTC
        )
        return monitoringEntity.time.convertToZonedDateTimeWithZ() != errorRez
    }
}