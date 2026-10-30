package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.operation.request.InAppShowError

internal interface InAppSerializationManager {

    fun serializeToShownInAppsString(shownInApps: Map<String, List<Long>>): String

    fun deserializeToShownInAppsMap(shownInApps: String): Map<String, List<Long>>

    fun serializeToInAppShownActionString(inAppId: String, timeToDisplay: String, tags: Map<String, String>?): String

    fun serializeToInAppTargetingString(inAppId: String, tags: Map<String, String>?): String

    fun serializeToInAppClickActionString(inAppId: String, tags: Map<String, String>?): String

    fun serializeToInAppShowErrorsString(inAppShowErrors: List<InAppShowError>): String

    fun deserializeToShownInApps(shownInApps: String): Set<String>
}
