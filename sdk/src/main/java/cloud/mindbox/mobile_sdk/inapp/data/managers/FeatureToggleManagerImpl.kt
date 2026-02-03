package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggle
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import java.util.concurrent.ConcurrentHashMap

internal class FeatureToggleManagerImpl : FeatureToggleManager {

    private val toggles = ConcurrentHashMap<FeatureToggle, Boolean>()

    override fun applyToggles(config: InAppConfigResponse?) {
        val featureToggles = config?.settings?.featureToggles

        toggles[FeatureToggle.SEND_INAPP_SHOW_ERROR] =
            featureToggles?.shouldSendInAppShowError ?: false
    }

    override fun isEnabled(toggle: FeatureToggle): Boolean {
        return toggles[toggle] ?: false
    }
}
