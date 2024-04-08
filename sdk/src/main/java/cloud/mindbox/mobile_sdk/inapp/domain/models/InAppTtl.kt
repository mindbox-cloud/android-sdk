package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.models.operation.response.TtlDto
import java.util.concurrent.TimeUnit

internal enum class InAppTtl(private val timeUnit: TimeUnit) {
    SECONDS(TimeUnit.SECONDS),
    MINUTES(TimeUnit.MINUTES),
    HOURS(TimeUnit.HOURS),
    DAYS(TimeUnit.DAYS);

    fun toMillis(value: Long): Long = timeUnit.toMillis(value)
}

internal data class InAppTtlData(val ttl: TtlDto?, val shouldCheckInAppTtl: Boolean)