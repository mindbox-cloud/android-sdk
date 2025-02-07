package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import kotlin.time.Duration.Companion.milliseconds

internal class MobileConfigSettingsManagerImpl(private val sessionStorageManager: SessionStorageManager) : MobileConfigSettingsManager {
    override fun saveSessionTime(config: InAppConfigResponse) {
        config.settings?.slidingExpiration?.inappSession?.parseTimeSpanToMillis()?.let { sessionTime ->
            if (sessionTime > 0) {
                sessionStorageManager.sessionTime = sessionTime.milliseconds
                mindboxLogI("Session time set to ${sessionStorageManager.sessionTime.inWholeMilliseconds} ms")
            }
        }
    }
}
