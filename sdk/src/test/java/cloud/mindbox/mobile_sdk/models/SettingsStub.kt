package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.models.operation.response.FeatureTogglesDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InappSettingsDto
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import cloud.mindbox.mobile_sdk.models.operation.response.SlidingExpirationDto

internal class SettingsStub {
    companion object {
        fun getSlidingExpiration(
            config: Milliseconds? = null,
            pushTokenKeepalive: Milliseconds? = null
        ): InAppConfigResponse =
            InAppConfigResponse(
                inApps = null,
                monitoring = null,
                settings = SettingsDto(
                    operations = emptyMap(),
                    ttl = null,
                    slidingExpiration = SlidingExpirationDto(
                        config = config,
                        pushTokenKeepalive = pushTokenKeepalive
                    ),
                    inapp = null,
                    featureToggles = FeatureTogglesDto(shouldSendInAppShowError = false)
                ),
                abtests = null
            )
    }

    fun getInappSettings(
        maxInappsPerSession: Int? = null,
        maxInappsPerDay: Int? = null,
        minIntervalBetweenShows: Milliseconds? = null
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
                ),
                featureToggles = FeatureTogglesDto(shouldSendInAppShowError = false)
            ),
            abtests = null
        )
}
