package cloud.mindbox.mobile_sdk.monitoring

import cloud.mindbox.mobile_sdk.models.operation.request.LogResponseDto

internal class MonitoringMapper {

    fun mapLogInfoToMonitoringEntity(timeStamp: Long, message: String): MonitoringEntity {
        return MonitoringEntity(0, timeStamp, message)
    }

    fun mapMonitoringEntityToLogInfo(monitoringStatus: String, requestId: String, monitoringEntityList: List<MonitoringEntity>): LogResponseDto {
        return monitoringEntityList.fold(
            initial = LogResponseDto(
                status = monitoringStatus, requestId = requestId, content = ""
            )
        ) { sum, term -> sum.copy(content = term.toString()) }
    }
}