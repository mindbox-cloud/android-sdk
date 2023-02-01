package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

interface LogStoringDataManager {

    fun isDatabaseMemorySizeExceeded(): Boolean
}