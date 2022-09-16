package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.operation.request.IdsRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationCheckRequest
import cloud.mindbox.mobile_sdk.models.operation.request.SegmentationDataRequest
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class InAppRepository {

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            MindboxLoggerImpl.d(InAppRepository::class.java, error.message ?: "")
        })


    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration) {
        repositoryScope.launch {
            with(GatewayManager.fetchInAppConfig(context, configuration))
            {
                MindboxPreferences.inAppConfig = this
            }
        }
    }

    private suspend fun checkSegmentation(
        context: Context,
        configuration: MindboxConfiguration,
        config: InAppConfigResponse,
    ): InAppDto {
        return suspendCoroutine { continuation ->
            repositoryScope.launch {
                GatewayManager.checkSegmentation(context,
                    configuration,
                    GatewayManager.convertBodyToJson(Gson().toJson(SegmentationCheckRequest(
                        config.inApps?.map { inAppDto ->
                            SegmentationDataRequest(IdsRequest(inAppDto.targeting?.segmentation))
                        }),
                        SegmentationCheckRequest::class.java))!!).customerSegmentations?.forEach { customerSegmentationInAppResponse ->
                    config.inApps?.forEach { inAppDto ->
                        if (inAppDto.targeting?.segment == customerSegmentationInAppResponse.segment?.ids?.externalId || customerSegmentationInAppResponse.segment == null) {
                            continuation.resume(inAppDto)
                        }
                    }
                }
            }
        }
    }

    fun listenInAppConfig(
        context: Context,
        configuration: MindboxConfiguration,
    ): Flow<Pair<InAppDto, EventType>> {
        return MindboxPreferences.inAppConfigFlow.combine(GatewayManager.eventFlow) { jsonConfig, event ->
            checkSegmentation(context,
                configuration,
                GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(PayloadDto::class.java,
                    TYPE_JSON_NAME)
                    .registerSubtype(PayloadDto.SimpleImage::class.java, SIMPLE_IMAGE_JSON_NAME))
                    .create()
                    .fromJson(jsonConfig, InAppConfigResponse::class.java)) to event
        }
    }

    companion object {
        private const val TYPE_JSON_NAME = "\$type"

        /**
         * Типы картинок
         **/
        private const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"
    }


}