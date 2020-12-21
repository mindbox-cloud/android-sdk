package cloud.mindbox.mobile_sdk.models


data class FullInitData(
    var token: String,
    var isTokenAvailable: Boolean,
    var installationId: String,
    var isNotificationsEnabled: Boolean
)

data class PartialInitData(
    var token: String,
    var isTokenAvailable: Boolean,
    var isNotificationsEnabled: Boolean
)