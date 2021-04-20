package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    val eventType: EventType,
    val transactionId: String = UUID.randomUUID().toString(),
    val enqueueTimestamp: Long = Date().time, // date of event creating
    val additionalFields: HashMap<String, String>? = null,
    val body: String? = null //json
)

internal enum class EventType(val operation: String, val endpoint: String) {
    APP_INSTALLED("MobilePush.ApplicationInstalled", "/v3/operations/async"),
    APP_INFO_UPDATED("MobilePush.ApplicationInfoUpdated", "/v3/operations/async"),
    PUSH_DELIVERED("", "/mobile-push/delivered"),
    PUSH_CLICKED("MobilePush.TrackClick", "/v3/operations/async"),
    TRACK_VISIT("TrackVisit", "/v1.1/customer/mobile-track-visit")
}

internal enum class EventParameters(val fieldName: String) {
    UNIQ_KEY("uniqKey")
}