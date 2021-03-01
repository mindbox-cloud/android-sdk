package cloud.mindbox.mobile_sdk.models


data class InitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val installationId: String,
    val isNotificationsEnabled: Boolean,
    val subscribe: Boolean
)

data class UpdateData(
    val token: String,
    val isTokenAvailable: Boolean,
    val isNotificationsEnabled: Boolean
)