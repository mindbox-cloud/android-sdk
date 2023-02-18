package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.SessionManager
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
    private val inAppMessageMapper: InAppMessageMapper,
    private val geoSerializationManager: GeoSerializationManager,
    private val sessionManager: SessionManager,
) : InAppGeoRepository {

    override suspend fun fetchGeo() {
        val configuration = DbManager.listenConfigurations().first()
        val geoTargeting = inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(
            geoTargetingDto = GatewayManager.checkGeoTargeting(
                context = context,
                configuration = configuration
            )
        )
        MindboxPreferences.inAppGeo =
            geoSerializationManager.serializeToGeoString(geoTargeting)
    }

    override fun getGeoFetchedStatus(): GeoFetchStatus {
        return LoggingExceptionHandler.runCatching(GeoFetchStatus.GEO_FETCH_ERROR) {
            sessionManager.geoFetchStatus
        }
    }

    override fun getGeo(): GeoTargeting {
        return LoggingExceptionHandler.runCatching(GeoTargeting("", "", "")) {
            geoSerializationManager.deserializeToGeoTargeting(MindboxPreferences.inAppGeo)
        }
    }
}