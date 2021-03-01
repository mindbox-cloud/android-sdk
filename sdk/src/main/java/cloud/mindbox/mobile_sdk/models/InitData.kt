package cloud.mindbox.mobile_sdk.models

internal data class InitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val installationId: String,
    val isNotificationsEnabled: Boolean,
    val subscribe: Boolean
)

internal data class UpdateData(
    val token: String,
    val isTokenAvailable: Boolean,
    val isNotificationsEnabled: Boolean
)