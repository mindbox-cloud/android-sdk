package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.toTimestamp

internal interface TimeProvider {
    fun currentTimeMillis(): Long

    fun currentTimestamp(): Timestamp
}

internal class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis() = System.currentTimeMillis()

    override fun currentTimestamp() = System.currentTimeMillis().toTimestamp()
}
