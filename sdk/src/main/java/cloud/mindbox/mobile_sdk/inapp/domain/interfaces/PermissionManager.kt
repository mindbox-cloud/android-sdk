package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

internal enum class PermissionStatus(val value: String) {
    GRANTED("granted"),
    DENIED("denied"),
    NOT_DETERMINED("notDetermined"),
    RESTRICTED("restricted"),
    LIMITED("limited"),
}

internal interface PermissionManager {

    fun getCameraPermissionStatus(): PermissionStatus

    fun getLocationPermissionStatus(): PermissionStatus

    fun getMicrophonePermissionStatus(): PermissionStatus

    fun getNotificationPermissionStatus(): PermissionStatus

    fun getPhotoLibraryPermissionStatus(): PermissionStatus

    fun isNotificationEnabled(): Boolean {
        return getNotificationPermissionStatus() == PermissionStatus.GRANTED
    }
}
