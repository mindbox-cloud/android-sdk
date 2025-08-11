package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Timestamp
import java.util.Calendar

internal fun getDayStartTimestamp(timestamp: Timestamp): Timestamp {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp.ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return Timestamp(calendar.timeInMillis)
}
