package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    val transactionId: String,
    var dateTimeOffset: Long,
    var enqueueTimestamp: Long, // date of event creating
    val body: String //json
)