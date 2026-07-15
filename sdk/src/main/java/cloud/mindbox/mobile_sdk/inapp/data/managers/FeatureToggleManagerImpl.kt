package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import java.util.concurrent.ConcurrentHashMap

internal const val SEND_INAPP_SHOW_ERROR_FEATURE = "MobileSdkShouldSendInAppShowError"
internal const val SEND_INAPP_TAGS_FEATURE = "MobileSdkShouldSendInAppTags"
internal const val PREWARM_INAPP_WEBVIEW_FEATURE = "MobileSdkShouldPrewarmInAppWebView"
internal const val CACHE_INAPP_WEBVIEW_FEATURE = "MobileSdkShouldCacheInAppWebView"

/** Every toggle is a kill switch: an absent/unknown key defaults to enabled. */
internal const val FEATURE_TOGGLE_DEFAULT: Boolean = true

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
        return toggles[key] ?: FEATURE_TOGGLE_DEFAULT
    }
}
