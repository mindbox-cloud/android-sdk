package cloud.mindbox.mobile_sdk.monitoring.data.rmappers

import cloud.mindbox.mobile_sdk.convertToZonedDateTimeWithZ
import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity
import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogResponse

internal class MonitoringMapper {

    fun mapLogInfoToMonitoringEntity(zonedDateTime: String, message: String): MonitoringEntity {
        return MonitoringEntity(time = zonedDateTime, log = message)
    }

    fun mapMonitoringEntityListToLogResponseList(logs: List<MonitoringEntity>): List<LogResponse> {
        return logs.map { monitoringEntity ->
            mapMonitoringEntityToLogResponse(monitoringEntity)
        }
    }

    fun mapMonitoringEntityToLogResponse(monitoringEntity: MonitoringEntity): LogResponse {
        return LogResponse(
            zonedDateTime = monitoringEntity.time.convertToZonedDateTimeWithZ(),
            log = monitoringEntity.log
        )
    }

    fun mapMonitoringEntityToLogInfo(
        monitoringStatus: String,
        requestId: String,
        monitoringEntityList: List<LogResponse>,
    ): LogResponseDto {
        return monitoringEntityList.fold(
            initial = LogResponseDto(
                status = monitoringStatus, requestId = requestId, content = mutableListOf()
            )
        ) { sum, term -> sum.content.add("${term.zonedDateTime} ${term.log}"); sum }
    }
}