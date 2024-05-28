package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTtlData
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.util.Date

internal class InAppConfigTtlValidator : Validator<InAppTtlData> {
    override fun isValid(item: InAppTtlData): Boolean {
        if (!item.shouldCheckInAppTtl) {
            mindboxLogI("Config was received from backend. Skip checking TTL")
            return true
        }

        return if (isConfigValid(ttl = item.ttl?.inApps)) {
            mindboxLogI("Ttl has not expired or ttl config is empty. Use inapps from cached config")
            true
        } else {
            mindboxLogI("In-Apps ttl was expired. Clean inApps list")
            false
        }
    }

    private fun isConfigValid(ttl: String?): Boolean {
        return LoggingExceptionHandler.runCatching(true) {
            ttl?.let {
                val configUpdatedTime = MindboxPreferences.inAppConfigUpdatedTime.toULong()
                val currentTime = System.currentTimeMillis().toULong()
                val ttlTime = ttl.parseTimeSpanToMillis().toULong()
                val safeTtlTime = minOf(Long.MAX_VALUE.toULong(), configUpdatedTime + ttlTime)
                mindboxLogI("Check In-Apps ttl. Current time $currentTime , config updated time $configUpdatedTime , ttl settings $ttlTime")
                mindboxLogI("Cached config valid to ${Date(safeTtlTime.toLong())}")
                val result = currentTime <= configUpdatedTime + ttlTime
                mindboxLogI("Cached config is active $result")
                result
            } ?: run {
                mindboxLogI("In-Apps ttl settings is empty")
                true
            }
        }
    }
}