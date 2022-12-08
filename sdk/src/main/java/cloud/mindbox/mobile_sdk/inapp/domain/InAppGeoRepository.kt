package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting

interface InAppGeoRepository {

    suspend fun fetchGeo()

    fun geoGeo(): GeoTargeting
}