package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.fromJsonTyped
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppErrorsWrapper
import cloud.mindbox.mobile_sdk.models.operation.request.InAppClickRequest
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowRequest
import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowError
import cloud.mindbox.mobile_sdk.models.operation.request.InAppTargetingRequest
import cloud.mindbox.mobile_sdk.toJsonTyped
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal class InAppSerializationManagerImpl(private val gson: Gson) : InAppSerializationManager {

    override fun serializeToInAppShownActionString(
        inAppId: String,
        timeToDisplay: String,
        tags: Map<String, String>?,
    ): String {
        return loggingRunCatching("") {
            gson.toJsonTyped<InAppShowRequest>(
                InAppShowRequest(
                    inAppId = inAppId,
                    timeToDisplay = timeToDisplay,
                    tags = tags,
                )
            )
        }
    }

    override fun serializeToInAppTargetingString(inAppId: String, tags: Map<String, String>?): String {
        return loggingRunCatching(defaultValue = "") {
            gson.toJsonTyped<InAppTargetingRequest>(InAppTargetingRequest(inAppId = inAppId, tags = tags))
        }
    }

    override fun serializeToInAppClickActionString(inAppId: String, tags: Map<String, String>?): String {
        return loggingRunCatching(defaultValue = "") {
            gson.toJsonTyped<InAppClickRequest>(
                InAppClickRequest(
                    inAppId = inAppId,
                    tags = tags,
                )
            )
        }
    }

    override fun serializeToShownInAppsString(shownInApps: Map<String, List<Long>>): String {
        return loggingRunCatching("") {
            gson.toJsonTyped<Map<String, List<Long>>>(shownInApps)
        }
    }

    override fun serializeToInAppShowErrorsString(
        inAppShowErrors: List<InAppShowError>
    ): String {
        return loggingRunCatching("") {
            gson.toJsonTyped<InAppErrorsWrapper>(InAppErrorsWrapper(inAppShowErrors))
        }
    }

    override fun deserializeToShownInAppsMap(shownInApps: String): Map<String, List<Long>> {
        return loggingRunCatching(hashMapOf()) {
            gson.fromJsonTyped<Map<String, List<Long>>>(shownInApps) ?: hashMapOf()
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
