package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import kotlinx.coroutines.flow.Flow

interface InAppGeoRepository {

    suspend fun fetchGeo()

    fun listenGeo(): Flow<GeoTargeting>
}