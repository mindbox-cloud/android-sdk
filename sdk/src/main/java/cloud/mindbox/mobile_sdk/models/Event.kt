package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    val eventType: EventType,
    val transactionId: String = UUID.randomUUID().toString(),
    val enqueueTimestamp: Long = Date().time, // date of event creating
    val additionalFields: HashMap<String, String>? = null,
    val body: String? = null //json
)

internal sealed class EventType(val operation: String, val endpoint: String) {

    object AppInstalled : EventType("MobilePush.ApplicationInstalled", "/v3/operations/async")

    object AppInfoUpdated : EventType("MobilePush.ApplicationInfoUpdated", "/v3/operations/async")

    object PushDelivered : EventType("", "/mobile-push/delivered")

    object PushClicked : EventType("MobilePush.TrackClick", "/v3/operations/async")

    object TrackVisit : EventType("TrackVisit", "/v1.1/customer/mobile-track-visit")

    internal class AsyncOperation(operation: String) : EventType(operation, "/v3/operations/async")

}

internal enum class EventParameters(val fieldName: String) {
    UNIQ_KEY("uniqKey")
}