package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    val transactionId: String= UUID.randomUUID().toString(),
    var dateTimeOffset: Long = -1L,
    var enqueueTimestamp: Long = Date().time, // date of event creating
    var eventType: EventType,
    var uniqKey: String?,
    val body: String? //json
)

internal enum class EventType(val operation: String, val endpoint: String) {
    APP_INSTALLED("MobileApplicationInstalled", "/v3/operations/async"),
    APP_INFO_UPDATED("MobileApplicationInfoUpdated", "/v3/operations/async"),
    PUSH_DELIVERED("", "/mobile-push/delivered")
}