package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

interface LogStoringDataChecker {

    fun isDatabaseMemorySizeExceeded(): Boolean
}