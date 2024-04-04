package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTtlData
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.operation.response.TtlParametersDto
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
            true.also {
                mindboxLogI("Ttl has not expired or ttl config is empty. Use inapps from cached config")
            }
        } else {
            false.also {
                mindboxLogI("In-Apps ttl was expired. Clean inApps list")
            }
        }
    }

    private fun isConfigValid(ttl: TtlParametersDto?): Boolean {
        return LoggingExceptionHandler.runCatching(true) {
            ttl?.let {
                val configUpdatedTime = MindboxPreferences.inAppConfigUpdatedTime
                val currentTime = System.currentTimeMillis()
                val ttlTime = ttl.unit.toMillis(ttl.value)
                val safeTtlTime = if (Long.MAX_VALUE - configUpdatedTime < ttlTime) Long.MAX_VALUE else configUpdatedTime + ttlTime
                mindboxLogI("Check In-Apps ttl. Current time $currentTime , config updated time $configUpdatedTime , ttl settings $ttlTime")
                mindboxLogI("Cached config valid to ${Date(safeTtlTime)}")
                val result = currentTime - ttlTime <= configUpdatedTime
                mindboxLogI("Cached config is active $result")
                result
            } ?: true.also {
                mindboxLogI("In-Apps ttl settings is empty")
            }
        }
    }
}