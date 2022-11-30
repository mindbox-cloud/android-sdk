package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.operation.request.InAppHandleRequest
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.TargetingDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class InAppRepositoryImpl(
    private val inAppMapper: InAppMessageMapper,
    private val gson: Gson,
    private val context: Context,
) : InAppRepository {

    override fun getShownInApps(): HashSet<String> {
        return LoggingExceptionHandler.runCatching(HashSet()) {
            if (MindboxPreferences.shownInAppIds.isBlank()) {
                HashSet()
            } else {
                gson.fromJson(MindboxPreferences.shownInAppIds,
                    object : TypeToken<HashSet<String>>() {}.type) ?: HashSet()
            }
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
        return inAppMapper.mapToSegmentationCheck(
            GatewayManager.checkSegmentation(context,
                configuration, inAppMapper.mapToSegmentationCheckRequest(config)))
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return MindboxEventManager.eventFlow
    }

    override fun saveShownInApp(id: String) {
        val shownInApps = getShownInApps().apply { add(id) }
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
            return@map inAppMapper.mapToInAppConfig(filteredConfig).also { inAppConfig ->
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
            gson.fromJson(inAppForm, FormDto::class.java)
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
            min <= InAppMessageManagerImpl.CURRENT_IN_APP_VERSION
        } ?: true
        val maxVersionValid = sdkVersion.maxVersion?.let { max ->
            max >= InAppMessageManagerImpl.CURRENT_IN_APP_VERSION
        } ?: true
        return minVersionValid && maxVersionValid
    }

    companion object {
        const val TYPE_JSON_NAME = "\$type"
        private const val IN_APP_OPERATION_VIEW_TYPE = "Inapp.Show"
        private const val IN_APP_OPERATION_CLICK_TYPE = "Inapp.Click"

        /**
         * Тargeting types
         **/
        const val TRUE_JSON_NAME = "true"
        const val AND_JSON_NAME = "and"
        const val OR_JSON_NAME = "or"
        const val SEGMENT_JSON_NAME = "segment"

        /**
         * In-app types
         **/
        const val SIMPLE_IMAGE_JSON_NAME = "simpleImage"


    }
}