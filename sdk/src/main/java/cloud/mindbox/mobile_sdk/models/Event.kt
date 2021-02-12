package cloud.mindbox.mobile_sdk.models

internal data class Event(
    val transactionId: String,
    var dateTimeOffset: Long,
    var enqueueTimestamp: Long, // date of event creating
    var eventType: EventType,
    var uniqKey: String?,
    val body: String? //json
)

internal enum class EventType(val operation: String, val endpoint: String) {
    APP_INSTALLED("MobileApplicationInstalled", "/v3/operations/async"),
    APP_INFO_UPDATED("MobileApplicationInfoUpdated", "/v3/operations/async"),
    PUSH_DELIVERED("", "/mobile-push/delivered")
}