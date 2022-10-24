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
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.android.volley.VolleyError
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppRepositoryImpl : InAppRepository {

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val inAppMapper: InAppMessageMapper by inject(InAppMessageMapper::class.java)


    private val shownInApps: HashSet<String> = if (MindboxPreferences.shownInAppIds.isBlank()) {
        HashSet()
    } else {
        Gson().fromJson(MindboxPreferences.shownInAppIds,
            object : TypeToken<HashSet<String>>() {}.type)
    }

    override fun getShownInApps(): HashSet<String> {
        return shownInApps
    }

    override fun sendInAppShown(context: Context, inAppId: String) {
        MindboxEventManager.inAppShown(context,
            IN_APP_OPERATION_VIEW_TYPE,
            Gson().toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }

    override fun sendInAppClicked(context: Context, inAppId: String) {
        MindboxEventManager.inAppClicked(context,
            IN_APP_OPERATION_CLICK_TYPE,
            Gson().toJson(InAppHandleRequest(inAppId), InAppHandleRequest::class.java))
    }


    override fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        repositoryScope.launch(CoroutineExceptionHandler { _, error ->
            if (error is VolleyError) {
                when (error.networkResponse?.statusCode) {
                    GatewayManager.CONFIG_NOT_FOUND -> {
                        MindboxLoggerImpl.w(ERROR_TAG, error.message ?: "")
                        MindboxPreferences.inAppConfig = ""
                    }
                    GatewayManager.CONFIG_NOT_UPDATED -> {
                        MindboxLoggerImpl.w(ERROR_TAG, error.message ?: "")
                        repositoryScope.launch {
                            MindboxPreferences.inAppConfigFlow.emit(MindboxPreferences.inAppConfig)
                        }
                    }
                    else -> {
                        MindboxLoggerImpl.e(ERROR_TAG, error.message ?: "")
                    }
                }
            } else {
                MindboxLoggerImpl.e(ERROR_TAG, error.message ?: "")
            }
        }) {
            with(GatewayManager.fetchInAppConfig(context, configuration))
            {
                MindboxPreferences.inAppConfig = this
            }
        }
    }

    override suspend fun fetchSegmentations(
        context: Context,
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): SegmentationCheckInApp {
        return suspendCoroutine { continuation ->
            repositoryScope.launch {
                continuation.resume(inAppMapper.mapSegmentationCheckResponseToSegmentationCheck(
                    GatewayManager.checkSegmentation(context,
                        configuration,
                        GatewayManager.convertBodyToJson(
                            Gson().toJson(SegmentationCheckRequest(
                                config.inApps.map { inAppDto ->
                                    SegmentationDataRequest(IdsRequest(inAppDto.targeting?.segmentation))
                                }),
                                SegmentationCheckRequest::class.java))!!)))
            }
        }
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return GatewayManager.eventFlow
    }

    override fun saveShownInApp(id: String) {
        shownInApps.add(id)
        MindboxPreferences.shownInAppIds =
            Gson().toJson(shownInApps, object : TypeToken<HashSet<String>>() {}.type)
    }

    override fun listenInAppConfig(): Flow<InAppConfig> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfig ->
            inAppMapper.mapInAppConfigResponseToInAppConfig(
                GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                    PayloadDto::class.java,
                    TYPE_JSON_NAME)
                    .registerSubtype(PayloadDto.SimpleImage::class.java,
                        SIMPLE_IMAGE_JSON_NAME))
                    .create()
                    .fromJson(inAppConfig,
                        InAppConfigResponse::class.java)
            )
        }
    }

    companion object {
        private const val TYPE_JSON_NAME = "\$type"
        private const val ERROR_TAG = "InAppRepositoryImpl"
        private const val IN_APP_OPERATION_VIEW_TYPE = "Inapp.View"
        private const val IN_APP_OPERATION_CLICK_TYPE = "Inapp.Click"

        /**
         * Типы картинок
         **/
        private const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"
    }


}