package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal interface InappSettingsManager {
    fun applySettings(config: InAppConfigResponse)
}
