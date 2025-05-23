package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Mindbox.logI
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal class InappSettingsManagerImpl : InappSettingsManager {
    override fun applySettings(config: InAppConfigResponse) {
        logI("""
            Settings for inapp from config:
            maxInappsPerSession = ${config.settings?.inapp?.maxInappsPerSession}
            maxInappsPerDay = ${config.settings?.inapp?.maxInappsPerDay}
            minIntervalBetweenShows = ${config.settings?.inapp?.minIntervalBetweenShows?.interval} ms
        """.trimIndent())
    }
}
