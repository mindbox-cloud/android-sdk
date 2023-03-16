package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoFetchStatus
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.flow.first

internal class InAppGeoRepositoryImpl(
    private val context: Context,
    private val inAppMapper: InAppMapper,
    private val geoSerializationManager: GeoSerializationManager,
    private val sessionStorageManager: SessionStorageManager,
) : InAppGeoRepository {

    override suspend fun fetchGeo() {
        val configuration = DbManager.listenConfigurations().first()
        val geoTargeting = inAppMapper.mapGeoTargetingDtoToGeoTargeting(
            geoTargetingDto = GatewayManager.checkGeoTargeting(
                context = context,
                configuration = configuration
            )
        )
        MindboxPreferences.inAppGeo =
            geoSerializationManager.serializeToGeoString(geoTargeting)
        sessionStorageManager.geoFetchStatus = GeoFetchStatus.GEO_FETCH_SUCCESS

    }

    override fun setGeoStatus(status: GeoFetchStatus) {
        sessionStorageManager.geoFetchStatus = status
    }

    override fun getGeoFetchedStatus(): GeoFetchStatus {
        return LoggingExceptionHandler.runCatching(GeoFetchStatus.GEO_FETCH_ERROR) {
            sessionStorageManager.geoFetchStatus
        }
    }

    override fun getGeo(): GeoTargeting {
        return LoggingExceptionHandler.runCatching(GeoTargeting("", "", "")) {
            geoSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        }
    }
}