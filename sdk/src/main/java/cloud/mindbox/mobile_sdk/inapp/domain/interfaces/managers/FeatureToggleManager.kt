package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse

internal interface FeatureToggleManager {

    fun applyToggles(config: InAppConfigResponse?)

    fun isEnabled(key: String): Boolean
}
