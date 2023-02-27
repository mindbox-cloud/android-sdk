package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

internal interface InAppSerializationManager {


    fun serializeToInAppHandledString(inAppId: String): String

    fun serializeToShownInAppsString(shownInApps: Set<String>): String

    fun deserializeToShownInApps(shownInApps: String): Set<String>
}