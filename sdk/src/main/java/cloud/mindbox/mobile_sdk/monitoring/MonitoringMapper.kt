package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto

internal class MonitoringMapper {

    fun mapLogInfoToMonitoringEntity(zonedDateTime: String, message: String): MonitoringEntity {
        return MonitoringEntity(id = 0, zonedDateTime = zonedDateTime, log = message)
    }

    fun mapMonitoringEntityListToLogResponseList(logs: List<MonitoringEntity>): List<LogResponse> {
        return logs.map { monitoringEntity ->
            LogResponse(
                time = monitoringEntity.zonedDateTime,
                log = monitoringEntity.log
            )
        }
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
        ) { sum, term -> sum.content.add(term.log); sum }
    }
}