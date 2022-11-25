package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class InAppRepositoryImpl(
    private val inAppMapper: InAppMessageMapper,
    private val gson: Gson,
    private val context: Context,
) : InAppRepository {

    override val shownInApps: HashSet<String>
        get() = LoggingExceptionHandler.runCatching(HashSet()) {
            if (MindboxPreferences.shownInAppIds.isBlank()) {
                HashSet()
            } else {
                gson.fromJson(MindboxPreferences.shownInAppIds,
                    object : TypeToken<HashSet<String>>() {}.type)
            }
        }


    override fun sendInAppShown(inAppId: String) {
        MindboxEventManager.inAppShown(context,
            IN_APP_OPERATION_VIEW_TYPE,
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }

    override fun sendInAppClicked(inAppId: String) {
        MindboxEventManager.inAppClicked(context,
            IN_APP_OPERATION_CLICK_TYPE,
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }


    override suspend fun fetchInAppConfig(configuration: MindboxConfiguration) {
        MindboxPreferences.inAppConfig =
            GatewayManager.fetchInAppConfig(context,
                configuration)
    }

    override suspend fun fetchSegmentations(
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): SegmentationCheckInApp {
        return inAppMapper.mapSegmentationCheckResponseToSegmentationCheck(
            GatewayManager.checkSegmentation(context,
                configuration, inAppMapper.mapInAppDtoToSegmentationCheckRequest(config)))
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return MindboxEventManager.eventFlow
    }

    override fun saveShownInApp(id: String) {
        shownInApps.add(id)
        MindboxPreferences.shownInAppIds =
            gson.toJson(shownInApps, object : TypeToken<HashSet<String>>() {}.type)
    }

    override fun listenInAppConfig(): Flow<InAppConfig?> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfig ->
            inAppMapper.mapInAppConfigResponseToInAppConfig(
                deserializeConfigToConfigDto(inAppConfig)).apply {
                MindboxLoggerImpl.d(this@InAppRepositoryImpl, "Providing config: $this")
            }
        }
    }

    override fun deserializeConfigToConfigDto(inAppConfig: String): InAppConfigResponse? {
        return runCatching {
            GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                PayloadDto::class.java,
                TYPE_JSON_NAME)
                .registerSubtype(PayloadDto.SimpleImage::class.java,
                    SIMPLE_IMAGE_JSON_NAME))
                .create()
                .fromJson(inAppConfig,
                    InAppConfigResponse::class.java)
        }.getOrNull()
    }

    companion object {
        const val TYPE_JSON_NAME = "\$type"
        private const val IN_APP_OPERATION_VIEW_TYPE = "Inapp.Show"
        private const val IN_APP_OPERATION_CLICK_TYPE = "Inapp.Click"

        /**
         * Типы картинок
         **/
        const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"
    }


}