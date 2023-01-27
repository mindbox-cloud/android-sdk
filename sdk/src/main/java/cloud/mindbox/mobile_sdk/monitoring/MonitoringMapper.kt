package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.convertToStringDate
import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto
import cloud.mindbox.mobile_sdk.models.operation.response.LogRequestDtoBlank

internal class MonitoringMapper {

    fun mapLogInfoToMonitoringEntity(timeStamp: Long, message: String): MonitoringEntity {
        return MonitoringEntity(id = 0, timestamp = timeStamp, log = message)
    }

    fun mapMonitoringEntityListToLogResponseList(logs: List<MonitoringEntity>): List<LogResponse> {
        return logs.map { monitoringEntity ->
            LogResponse(
                time = monitoringEntity.timestamp.convertToStringDate(),
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