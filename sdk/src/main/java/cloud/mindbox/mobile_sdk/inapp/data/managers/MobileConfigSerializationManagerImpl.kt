package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.data.dto.*
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.FormBlankDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadBlankDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
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

    override fun deserializeToFrequencyDtoBlank(frequencyString: JsonObject?): FrequencyDto? {
        val result = runCatching {
            gson.fromJson(frequencyString, FrequencyDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $frequencyString",
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
        val blankResult = runCatching {
            gson.fromJson(inAppForm, FormBlankDto::class.java)
        }
        blankResult.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $inAppForm",
                exception = error
            )
        }
        val result =
            FormDto(
                variants = blankResult.getOrNull()?.variants?.filterNotNull()
                    ?.map { payloadBlankDto ->
                        when (payloadBlankDto) {
                            is PayloadBlankDto.ModalWindowBlankDto -> {
                                PayloadDto.ModalWindowDto(
                                    content = PayloadDto.ModalWindowDto.ContentDto(
                                        background = BackgroundDto(
                                            layers = payloadBlankDto.content?.background?.layers?.mapNotNull {
                                                deserializeToBackgroundLayersDto(it as JsonObject)
                                            }),
                                        elements = payloadBlankDto.content?.elements?.mapNotNull {
                                            deserializeToElementDto(it)
                                        }
                                    ), type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME
                                )
                            }
                            is PayloadBlankDto.SnackBarBlankDto -> {
                                PayloadDto.SnackbarDto(
                                    content = PayloadDto.SnackbarDto.ContentDto(
                                        background = BackgroundDto(layers = payloadBlankDto.content?.background?.layers?.mapNotNull {
                                            deserializeToBackgroundLayersDto(it as JsonObject)
                                        }),
                                        elements = payloadBlankDto.content?.elements?.mapNotNull {
                                            deserializeToElementDto(it)
                                        },
                                        position = PayloadDto.SnackbarDto.ContentDto.PositionDto(
                                            gravity = PayloadDto.SnackbarDto.ContentDto.PositionDto.GravityDto(
                                                horizontal = payloadBlankDto.content?.position?.gravity?.horizontal,
                                                vertical = payloadBlankDto.content?.position?.gravity?.vertical
                                            ),
                                            margin = PayloadDto.SnackbarDto.ContentDto.PositionDto.MarginDto(
                                                bottom = payloadBlankDto.content?.position?.margin?.bottom,
                                                kind = payloadBlankDto.content?.position?.margin?.kind,
                                                left = payloadBlankDto.content?.position?.margin?.left,
                                                right = payloadBlankDto.content?.position?.margin?.right,
                                                top = payloadBlankDto.content?.position?.margin?.top
                                            )
                                        )
                                    ), type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME
                                )
                            }
                        }
                    })
        if (result.variants.isNullOrEmpty()) return null
        return result
    }

    private fun deserializeToElementDto(element: JsonObject?): ElementDto? {
        if (element == null) return null
        val result = runCatching {
            gson.fromJson(element, ElementDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $element",
                exception = error
            )
        }
        return result.getOrNull()
    }

    private fun deserializeToBackgroundLayersDto(layer: JsonObject?): BackgroundDto.LayerDto? {
        if (layer == null) return null
        val result = runCatching {
            gson.fromJson(layer, BackgroundDto.LayerDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this@MobileConfigSerializationManagerImpl,
                message = "Failed to parse JsonObject: $layer",
                exception = error
            )
        }
        return result.getOrNull()
    }
}