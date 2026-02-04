package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import java.util.concurrent.ConcurrentHashMap

internal const val SEND_INAPP_SHOW_ERROR_FEATURE = "shouldSendInAppShowError"

internal class FeatureToggleManagerImpl : FeatureToggleManager {

    private val toggles = ConcurrentHashMap<String, Boolean>()

    override fun applyToggles(config: InAppConfigResponse?) {
        toggles.clear()
        config?.settings?.featureToggles?.forEach { (key, value) ->
            value?.let {
                toggles[key] = value
            }
        }
    }

    override fun isEnabled(key: String): Boolean {
        return toggles[key] ?: false
    }
}
