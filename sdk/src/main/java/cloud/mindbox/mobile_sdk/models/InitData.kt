package cloud.mindbox.mobile_sdk.models

private const val INIT_DATA_VERSION = 0

internal data class InitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val installationId: String,
    val isNotificationsEnabled: Boolean,
    val subscribe: Boolean,
    private val version: Int = INIT_DATA_VERSION
)

internal data class UpdateData(
    val token: String,
    val isTokenAvailable: Boolean,
    val isNotificationsEnabled: Boolean,
    val version: Int
)

internal data class TrackClickData(
    val messageUniqueKey: String,
    val buttonUniqueKey: String
)