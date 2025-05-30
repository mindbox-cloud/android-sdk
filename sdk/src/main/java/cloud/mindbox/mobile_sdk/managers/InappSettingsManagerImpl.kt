package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Mindbox.logI
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppShowLimitsSettings
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal class InappSettingsManagerImpl(val sessionStorageManager: SessionStorageManager) : InappSettingsManager {
    override fun applySettings(config: InAppConfigResponse) {
        logI("""
            Settings for inapp from config:
            maxInappsPerSession = ${config.settings?.inapp?.maxInappsPerSession}
            maxInappsPerDay = ${config.settings?.inapp?.maxInappsPerDay}
            minIntervalBetweenShows = ${config.settings?.inapp?.minIntervalBetweenShows?.interval} ms
        """.trimIndent())

        sessionStorageManager.inAppShowLimitsSettings = InAppShowLimitsSettings(
            maxInappsPerSession = config.settings?.inapp?.maxInappsPerSession ?: 0,
            maxInappsPerDay = config.settings?.inapp?.maxInappsPerDay ?: 0,
            minIntervalBetweenShows = config.settings?.inapp?.minIntervalBetweenShows ?: Milliseconds(0)
        )
    }
}
