package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal class InAppSerializationManagerImpl(private val gson: Gson) : InAppSerializationManager {

    override fun serializeToInAppHandledString(inAppId: String): String {
        return LoggingExceptionHandler.runCatching("") {
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java)
        }
    }

    override fun serializeToShownInAppsString(shownInApps: Map<String, List<Long>>): String {
        return loggingRunCatching("") {
            gson.toJson(shownInApps, object : TypeToken<HashMap<String, List<Long>>>() {}.type)
        }
    }

    override fun deserializeToShownInAppsMap(shownInApps: String): Map<String, List<Long>> {
        return loggingRunCatching(hashMapOf()) {
            gson.fromJson(shownInApps, object : TypeToken<HashMap<String, List<Long>>>() {}.type) ?: hashMapOf()
        }
    }

    override fun deserializeToShownInApps(shownInApps: String): Set<String> {
        return LoggingExceptionHandler.runCatching(HashSet()) {
            gson.fromJson(
                shownInApps,
                object : TypeToken<HashSet<String>>() {}.type
            ) ?: emptySet()
        }
    }
}
