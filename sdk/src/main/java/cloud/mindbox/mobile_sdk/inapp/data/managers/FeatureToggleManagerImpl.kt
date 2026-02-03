package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import java.util.concurrent.atomic.AtomicBoolean

internal class FeatureToggleManagerImpl : FeatureToggleManager {

    private val shouldSendInAppShowError = AtomicBoolean(false)

    override fun applyToggles(config: InAppConfigResponse?) {
        shouldSendInAppShowError.set(config?.settings?.featureToggles?.shouldSendInAppShowError ?: false)
    }

    override fun shouldSendInAppShowError(): Boolean {
        return shouldSendInAppShowError.get()
    }
}
