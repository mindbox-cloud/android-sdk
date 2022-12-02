package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
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

    override fun listenInAppConfig(): Flow<InAppConfig?> {
        return MindboxPreferences.inAppConfigFlow.map { inAppConfigString ->
            MindboxLoggerImpl.d(
                parent = this@InAppRepositoryImpl,
                message = "CachedConfig : $inAppConfigString"
            )
            val configBlank = deserializeToConfigDtoBlank(inAppConfigString)
            val filteredInApps = configBlank?.inApps
                ?.filter { validateInAppVersion(it) }
                ?.map { inAppDtoBlank ->
                    inAppMapper.mapToInAppDto(
                        inAppDtoBlank = inAppDtoBlank,
                        formDto = deserializeToInAppFormDto(inAppDtoBlank.form),
                    )
                }
            val filteredConfig = InAppConfigResponse(
                inApps = filteredInApps
            )
            return@map inAppMapper.mapInAppConfigResponseToInAppConfig(filteredConfig).also { inAppConfig ->
                MindboxLoggerImpl.d(
                    parent = this@InAppRepositoryImpl,
                    message = "Providing config: $inAppConfig"
                )
            }
        }
    }

    private fun deserializeToConfigDtoBlank(inAppConfig: String): InAppConfigResponseBlank? {
        val result = runCatching {
            gson.fromJson(inAppConfig, InAppConfigResponseBlank::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@InAppRepositoryImpl,
                message = "Failed to parse inAppConfig: $inAppConfig",
                exception = error
            )
        }
        return result.getOrNull()
    }

    private fun deserializeToInAppFormDto(inAppForm: JsonObject?): FormDto? {
        val result = runCatching {
            GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                PayloadDto::class.java,
                TYPE_JSON_NAME)
                .registerSubtype(PayloadDto.SimpleImage::class.java,
                    SIMPLE_IMAGE_JSON_NAME))
                .create()
                .fromJson(inAppForm,
                    FormDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@InAppRepositoryImpl,
                message = "Failed to parse JsonObject: $inAppForm",
                exception = error
            )
        }
        return result.getOrNull()
    }

    private fun validateInAppVersion(inAppDto: InAppConfigResponseBlank.InAppDtoBlank): Boolean {
        val sdkVersion = inAppDto.sdkVersion ?: return false
        val minVersionValid = sdkVersion.minVersion?.let { min ->
            min <= InAppMessageManager.CURRENT_IN_APP_VERSION
        } ?: true
        val maxVersionValid = sdkVersion.maxVersion?.let { max ->
            max >= InAppMessageManager.CURRENT_IN_APP_VERSION
        } ?: true
        return minVersionValid && maxVersionValid
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