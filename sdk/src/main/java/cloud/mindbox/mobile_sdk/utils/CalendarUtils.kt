package cloud.mindbox.mobile_sdk.utils

import java.util.Calendar

internal fun getDayBounds(currentTimestamp: Long): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = currentTimestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = calendar.timeInMillis

    calendar.add(Calendar.DAY_OF_MONTH, 1)
    val endOfDay = calendar.timeInMillis

    return startOfDay to endOfDay
}
