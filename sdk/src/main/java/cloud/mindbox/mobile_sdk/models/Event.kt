package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    var eventType: EventType,
    val transactionId: String = UUID.randomUUID().toString(),
    var enqueueTimestamp: Long = Date().time, // date of event creating
    var additionalFields: HashMap<String, String>? = null,
    var body: String? = null //json
)

internal enum class EventType(val operation: String, val endpoint: String) {
    APP_INSTALLED("MobilePush.ApplicationInstalled", "/v3/operations/async"),
    APP_INFO_UPDATED("MobilePush.ApplicationInfoUpdated", "/v3/operations/async"),
    PUSH_DELIVERED("", "/mobile-push/delivered"),
    PUSH_CLICKED("MobilePush.TrackClick", "/v3/operations/async")
}

internal enum class EventParameters(val fieldName: String) {
    UNIQ_KEY("uniqKey")
}