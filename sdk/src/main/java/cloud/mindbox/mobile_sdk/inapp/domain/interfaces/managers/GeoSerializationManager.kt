package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting

internal interface GeoSerializationManager {


    fun deserializeToGeoTargeting(inAppGeo: String): GeoTargeting

    fun serializeToGeoString(inAppGeo: GeoTargeting): String
}