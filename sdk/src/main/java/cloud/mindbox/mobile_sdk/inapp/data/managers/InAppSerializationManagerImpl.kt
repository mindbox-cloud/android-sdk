package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal class InAppSerializationManagerImpl(private val gson: Gson) : InAppSerializationManager {

    override fun serializeToInAppHandledString(inAppId: String): String {
        return LoggingExceptionHandler.runCatching("") {
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java)
        }
    }

    override fun serializeToShownInAppsString(
        shownInApps: MutableSet<String>
    ): String {
        return LoggingExceptionHandler.runCatching("") {
            gson.toJson(shownInApps, object : TypeToken<HashSet<String>>() {}.type)
        }
    }

    override fun deserializeToShownInApps(shownInApps: String): MutableSet<String> {
        return LoggingExceptionHandler.runCatching(HashSet()) {
            gson.fromJson(
                shownInApps,
                object : TypeToken<HashSet<String>>() {}.type
            ) ?: HashSet()
        }
    }
}