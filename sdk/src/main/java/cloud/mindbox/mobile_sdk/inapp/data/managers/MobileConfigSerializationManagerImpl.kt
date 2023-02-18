package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import com.google.gson.Gson
import com.google.gson.JsonObject

internal class MobileConfigSerializationManagerImpl(private val gson: Gson) :
    MobileConfigSerializationManager {


    override fun deserializeToInAppTargetingDto(inAppTreeTargeting: JsonObject?): TreeTargetingDto? {
        val result = runCatching {
            gson.fromJson(inAppTreeTargeting, TreeTargetingDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $inAppTreeTargeting",
                exception = error
            )
        }
        return result.getOrNull()
    }

    override fun deserializeToConfigDtoBlank(inAppConfig: String): InAppConfigResponseBlank? {
        val result = runCatching {
            gson.fromJson(inAppConfig, InAppConfigResponseBlank::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse inAppConfig: $inAppConfig",
                exception = error
            )
        }
        return result.getOrNull()
    }

    override fun deserializeToInAppFormDto(inAppForm: JsonObject?): FormDto? {
        val result = runCatching {
            gson.fromJson(inAppForm, FormDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $inAppForm",
                exception = error
            )
        }
        return result.getOrNull()
    }
}