package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

internal interface InAppSerializationManager {

    fun serializeToShownInAppsString(shownInApps: Map<String, List<Long>>): String

    fun deserializeToShownInAppsMap(shownInApps: String): Map<String, List<Long>>

    fun serializeToInAppHandledString(inAppId: String): String

    fun deserializeToShownInApps(shownInApps: String): Set<String>
}
