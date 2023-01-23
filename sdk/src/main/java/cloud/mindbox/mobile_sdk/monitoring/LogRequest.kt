package cloud.mindbox.mobile_sdk.monitoring

internal data class LogRequest(
    val requestId: String,
    val deviceId: String,
    val from: String,
    val to: String,
)