package cloud.mindbox.mobile_sdk.models

internal data class Event(
    val transactionId: String,
    var dateTimeOffset: Long,
    var enqueueTimestamp: Long, // date of event creating
    var eventType: EventType,
    val body: String //json
)

internal enum class EventType(val type: String) {
    APP_INSTALLED("MobileApplicationInstalled"),
    APP_INFO_UPDATED("MobileApplicationInfoUpdated")
}