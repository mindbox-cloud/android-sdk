package cloud.mindbox.mobile_sdk.models

import androidx.annotation.StringDef

private const val INIT_DATA_VERSION = 0

internal const val DIRECT = "direct"
internal const val LINK = "link"
internal const val PUSH = "push"

internal data class InitData(
    val token: String,
    val isTokenAvailable: Boolean,
    val installationId: String,
    val lastDeviceUuid: String,
    val isNotificationsEnabled: Boolean,
    val subscribe: Boolean,
    val instanceId: String,
    private val version: Int = INIT_DATA_VERSION
)

internal data class UpdateData(
    val token: String,
    val isTokenAvailable: Boolean,
    val isNotificationsEnabled: Boolean,
    val instanceId: String,
    val version: Int
)

internal data class TrackClickData(
    val messageUniqueKey: String,
    val buttonUniqueKey: String
)

internal data class TrackVisitData(
    val ianaTimeZone: String,
    val endpointId: String,
    @TrackVisitSource val source: String? = null,
    val requestUrl: String? = null
)

@StringDef(DIRECT, LINK, PUSH)
internal annotation class TrackVisitSource
