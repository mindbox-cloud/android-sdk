package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal interface MobileConfigSettingsManager {
    fun saveSessionTime(config: InAppConfigResponse)

    fun checkPushTokenKeepalive(config: InAppConfigResponse)
}
