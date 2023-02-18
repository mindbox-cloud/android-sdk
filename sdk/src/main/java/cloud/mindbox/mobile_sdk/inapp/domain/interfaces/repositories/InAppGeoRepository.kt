package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting

interface InAppGeoRepository {

    suspend fun fetchGeo()

    fun getGeoFetchedStatus(): GeoFetchStatus

    fun getGeo(): GeoTargeting
}