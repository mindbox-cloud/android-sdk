package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal enum class FeatureToggle {
    SEND_INAPP_SHOW_ERROR
}

internal interface FeatureToggleManager {

    fun applyToggles(config: InAppConfigResponse?)

    fun isEnabled(toggle: FeatureToggle): Boolean
}
