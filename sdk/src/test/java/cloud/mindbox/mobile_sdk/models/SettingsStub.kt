package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InappSettingsDto
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import cloud.mindbox.mobile_sdk.models.operation.response.SlidingExpirationDto

internal class SettingsStub {
    companion object {
        fun getSlidingExpiration(
            timeSpan: String = "",
            pushTokenKeepalive: String? = null
        ): InAppConfigResponse =
            InAppConfigResponse(
                inApps = null,
                monitoring = null,
                settings = SettingsDto(
                    operations = emptyMap(),
                    ttl = null,
                    slidingExpiration = SlidingExpirationDto(
                        config = timeSpan,
                        pushTokenKeepalive = pushTokenKeepalive
                    ),
                    inapp = null
                ),
                abtests = null
            )
    }

    fun getInappSettings(
        maxInappsPerSession: Int? = null,
        maxInappsPerDay: Int? = null,
        minIntervalBetweenShows: Long? = null
    ): InAppConfigResponse =
        InAppConfigResponse(
            inApps = null,
            monitoring = null,
            settings = SettingsDto(
                operations = emptyMap(),
                ttl = null,
                slidingExpiration = null,
                inapp = InappSettingsDto(
                    maxInappsPerSession = maxInappsPerSession,
                    maxInappsPerDay = maxInappsPerDay,
                    minIntervalBetweenShows = minIntervalBetweenShows
                )
            ),
            abtests = null
        )
}
