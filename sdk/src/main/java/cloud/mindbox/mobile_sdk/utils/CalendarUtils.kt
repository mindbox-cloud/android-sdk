package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Timestamp
import java.util.Calendar
import java.util.concurrent.TimeUnit

internal fun getDayBounds(timestamp: Timestamp): Pair<Timestamp, Timestamp> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp.value
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = Timestamp(calendar.timeInMillis)
    val endOfDay = Timestamp(startOfDay.value + TimeUnit.DAYS.toMillis(1))

    return startOfDay to endOfDay
}
