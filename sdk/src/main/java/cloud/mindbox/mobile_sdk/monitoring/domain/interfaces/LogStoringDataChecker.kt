package cloud.mindbox.mobile_sdk.monitoring.domain.interfaces

internal interface LogStoringDataChecker {

    fun isDatabaseMemorySizeExceeded(): Boolean
}
