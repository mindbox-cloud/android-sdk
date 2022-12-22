package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.domain.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class InAppGeoRepositoryImpl(
    private val context: Context,
    private val inAppMessageMapper: InAppMessageMapper,
    private val gson: Gson,
) : InAppGeoRepository {
    override suspend fun fetchGeo() {
        val configuration = DbManager.listenConfigurations().first()
        val geoTargeting = inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(
            geoTargetingDto = GatewayManager.checkGeoTargeting(
                context = context,
                configuration = configuration
            )
        )
        MindboxPreferences.inAppGeo = gson.toJson(geoTargeting)
    }

    override fun geoGeo(): GeoTargeting {
        return LoggingExceptionHandler.runCatching(GeoTargeting("", "", "")) {
            if (MindboxPreferences.inAppGeo.isNotBlank()) {
                gson.fromJson(MindboxPreferences.inAppGeo, GeoTargeting::class.java)
            } else {
                GeoTargeting("", "", "")
            }
        }
    }
}