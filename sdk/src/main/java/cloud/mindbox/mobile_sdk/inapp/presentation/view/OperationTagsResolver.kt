package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import com.google.gson.JsonObject

internal class OperationTagsResolver(
    private val mobileConfigRepository: MobileConfigRepository,
    private val featureToggleManager: FeatureToggleManager,
) {

    companion object {
        private const val INAPP_ID_FIELD = "inappId"
    }

    fun resolve(
        request: JsonObject,
        hostInAppId: String,
        hostTags: Map<String, String>?,
    ): Map<String, String>? {
        val inAppId = requestedInAppId(request)
        if (inAppId == null || inAppId == hostInAppId) return hostTags
        val inApp = mobileConfigRepository.findInAppInCurrentConfig(inAppId) ?: run {
            mindboxLogW("[WebView] Bridge: operation from $hostInAppId names in-app $inAppId, which is not in the config; sending it without SDK tags")
            return null
        }
        return inApp.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))
    }

    private fun requestedInAppId(request: JsonObject): String? {
        val element = request.get(INAPP_ID_FIELD)
        if (element == null || element.isJsonNull) return null
        return requireBridgeNotNull(element.asNonEmptyStringOrNull(), BridgeErrorCode.INVALID_PAYLOAD) {
            "inappId must be a non-empty string, got $element"
        }
    }
}
