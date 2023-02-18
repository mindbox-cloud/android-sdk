package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson

internal class GeoSerializationManagerImpl(private val gson: Gson) : GeoSerializationManager {

    override fun deserializeToGeoTargeting(inAppGeo: String): GeoTargeting {
        return LoggingExceptionHandler.runCatching(GeoTargeting("", "", "")) {
            if (inAppGeo.isEmpty()) {
                GeoTargeting("", "", "")
            } else {
                gson.fromJson(MindboxPreferences.inAppGeo, GeoTargeting::class.java)
            }
        }
    }

    override fun serializeToGeoString(inAppGeo: GeoTargeting): String {
        return LoggingExceptionHandler.runCatching("") {
            gson.toJson(inAppGeo)
        }
    }

}