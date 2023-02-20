package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting

internal interface InAppGeoRepository {

    suspend fun fetchGeo()

    fun setGeoStatus(status: GeoFetchStatus)

    fun getGeoFetchedStatus(): GeoFetchStatus

    fun getGeo(): GeoTargeting
}