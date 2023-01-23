package cloud.mindbox.mobile_sdk.models.operation.request

internal data class LogResponseDto(
    val status: String,
    val requestId: String,
    val content: String
)
