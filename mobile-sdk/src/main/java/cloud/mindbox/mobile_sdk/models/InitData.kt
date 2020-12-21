package cloud.mindbox.mobile_sdk.models

abstract class InitData()

data class FullInitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val installationId: String,
    val isNotificationsEnabled: Boolean
): InitData()

data class PartialInitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val isNotificationsEnabled: Boolean
): InitData()