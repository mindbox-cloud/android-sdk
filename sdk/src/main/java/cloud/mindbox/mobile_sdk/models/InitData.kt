package cloud.mindbox.mobile_sdk.models


data class InitData(
    var token: String,
    var isTokenAvailable: Boolean,
    var installationId: String,
    var isNotificationsEnabled: Boolean,
    var subscribe: Boolean
)

data class UpdateData(
    var token: String,
    var isTokenAvailable: Boolean,
    var isNotificationsEnabled: Boolean
)