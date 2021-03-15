package cloud.mindbox.mobile_sdk.models

import java.util.*

data class Event(
    val transactionId: String = UUID.randomUUID().toString(),
    var enqueueTimestamp: Long = Date().time, // date of event creating
    var eventType: EventType,
    var additionalFields: HashMap<String, String>?,
    val body: String?, //json
    var iSuccess: Boolean? = null,
    var requestTime: Long? = null
)

enum class EventType(val operation: String, val endpoint: String) {
    APP_INSTALLED("MobilePush.ApplicationInstalled", "/v3/operations/async"),
    APP_INFO_UPDATED("MobilePush.ApplicationInfoUpdated", "/v3/operations/async"),
    PUSH_DELIVERED("", "/mobile-push/delivered")
}

internal enum class EventParameters(val fieldName: String) {
    UNIQ_KEY("uniqKey")
}