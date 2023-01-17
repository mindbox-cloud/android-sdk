package cloud.mindbox.mobile_sdk.monitoring

import com.google.gson.Gson

class MonitoringMapper(private val gson: Gson) {

    fun mapLogInfoToMonitoringEntity(timeStamp: Long, message: String): MonitoringEntity {
        return MonitoringEntity(0, timeStamp, message)
    }

    fun mapMonitoringEntityToLogInfo(monitoringEntity: MonitoringEntity): String {
        return gson.toJson(monitoringEntity)
    }
}