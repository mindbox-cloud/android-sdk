package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

internal interface InAppSerializationManager {


    fun serializeToInAppHandledString(inAppId: String): String

    fun serializeToShownInAppsString(shownInApps: HashSet<String>, inAppId: String): String

    fun deserializeToShownInApps(shownInApps: String): HashSet<String>
}