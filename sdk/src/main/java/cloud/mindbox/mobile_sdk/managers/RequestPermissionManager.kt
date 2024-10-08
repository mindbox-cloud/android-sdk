package cloud.mindbox.mobile_sdk.managers

internal interface RequestPermissionManager {

    fun increaseRequestCounter()

    fun decreaseRequestCounter()

    fun getRequestCount(): Int
}
