package cloud.mindbox.mobile_sdk.models

import androidx.annotation.StringDef
import com.google.gson.annotations.SerializedName

private const val INIT_DATA_VERSION = 0

internal const val DIRECT = "direct"
internal const val LINK = "link"
internal const val PUSH = "push"

internal data class InitData(
    @SerializedName("token") val token: String,
    @SerializedName("isTokenAvailable") val isTokenAvailable: Boolean,
    @SerializedName("previousInstallationId") val previousInstallationId: String,
    @SerializedName("previousDeviceUUID") val previousDeviceUUID: String,
    @SerializedName("isNotificationsEnabled") val isNotificationsEnabled: Boolean,
    @SerializedName("subscribe") val subscribe: Boolean,
    @SerializedName("instanceId") val instanceId: String,
    @SerializedName("version") private val version: Int = INIT_DATA_VERSION
)

internal data class UpdateData(
    @SerializedName("token") val token: String,
    @SerializedName("isTokenAvailable") val isTokenAvailable: Boolean,
    @SerializedName("isNotificationsEnabled") val isNotificationsEnabled: Boolean,
    @SerializedName("instanceId") val instanceId: String,
    @SerializedName("version") val version: Int
)

internal data class TrackClickData(
    @SerializedName("messageUniqueKey") val messageUniqueKey: String,
    @SerializedName("buttonUniqueKey") val buttonUniqueKey: String?
)

internal data class TrackVisitData(
    @SerializedName("ianaTimeZone") val ianaTimeZone: String,
    @SerializedName("endpointId") val endpointId: String,
    @SerializedName("source") @TrackVisitSource val source: String? = null,
    @SerializedName("requestUrl") val requestUrl: String? = null
)

@StringDef(DIRECT, LINK, PUSH)
internal annotation class TrackVisitSource
