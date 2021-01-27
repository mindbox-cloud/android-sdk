package cloud.mindbox.mobile_sdk.models

import java.util.*

internal data class Event(
    var transactionId: String = UUID.randomUUID().toString(),
    var dateTimeOffset: Long,
    var enqueueTimestamp: Long, // date of event creating
    val data: String //json
)