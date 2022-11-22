package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
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
import org.koin.java.KoinJavaComponent.inject

internal class InAppRepositoryImpl : InAppRepository {

    private val inAppMapper: InAppMessageMapper by inject(InAppMessageMapper::class.java)
    private val gson: Gson by inject(Gson::class.java)

    private val shownInApps: HashSet<String> = LoggingExceptionHandler.runCatching(HashSet()) {
        if (MindboxPreferences.shownInAppIds.isBlank()) {
            HashSet()
        } else {
            gson.fromJson(MindboxPreferences.shownInAppIds,
                object : TypeToken<HashSet<String>>() {}.type)
        }
    }


    override fun getShownInApps(): HashSet<String> {
        return shownInApps
    }

    override fun sendInAppShown(context: Context, inAppId: String) {
        MindboxEventManager.inAppShown(context,
            IN_APP_OPERATION_VIEW_TYPE,
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }

    override fun sendInAppClicked(context: Context, inAppId: String) {
        MindboxEventManager.inAppClicked(context,
            IN_APP_OPERATION_CLICK_TYPE,
            gson.toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }


    override suspend fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        MindboxPreferences.inAppConfig =
            GatewayManager.fetchInAppConfig(context,
                configuration)
    }

    override suspend fun fetchSegmentations(
        context: Context,
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

    override fun listenInAppConfig(): Flow<InAppConfig> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfigDto ->
            val config = inAppMapper.mapInAppConfigResponseToInAppConfig(
                GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                    PayloadDto::class.java,
                    TYPE_JSON_NAME)
                    .registerSubtype(PayloadDto.SimpleImage::class.java,
                        SIMPLE_IMAGE_JSON_NAME))
                    .create()
                    .fromJson(inAppConfigDto,
                        InAppConfigResponse::class.java)
            )
            MindboxLoggerImpl.d(this, "Providing config: $config")
            config
        }
    }

    companion object {
        private const val TYPE_JSON_NAME = "\$type"
        private const val IN_APP_OPERATION_VIEW_TYPE = "Inapp.Show"
        private const val IN_APP_OPERATION_CLICK_TYPE = "Inapp.Click"

        /**
         * Типы картинок
         **/
        private const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"
    }


}