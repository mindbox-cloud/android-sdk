package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.domain.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.VolleyError
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class InAppGeoRepositoryImpl(
    private val context: Context,
    private val inAppMessageMapper: InAppMessageMapper,
    private val gson: Gson,
) : InAppGeoRepository {
    override suspend fun fetchGeo() {
        DbManager.listenConfigurations().map { configuration ->
            inAppMessageMapper.mapGeoTargetingDtoToGeoTargeting(GatewayManager.checkGeoTargeting(
                context,
                configuration))
        }.first { geoTargeting ->
            MindboxPreferences.inAppGeo = gson.toJson(geoTargeting)
            true
        }
    }

    override fun geoGeo(): GeoTargeting {
        return LoggingExceptionHandler.runCatching(GeoTargeting("", "", "")) {
            gson.fromJson(MindboxPreferences.inAppGeo, GeoTargeting::class.java)
        }
    }
}