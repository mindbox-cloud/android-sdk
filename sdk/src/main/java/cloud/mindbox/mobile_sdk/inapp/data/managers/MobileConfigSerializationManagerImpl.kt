package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.inapp.data.dto.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.*
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank.InAppDtoBlank
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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

    override fun deserializeToFrequencyDto(frequencyString: JsonObject?): FrequencyDto? {
        val result = runCatching {
            gson.fromJson(frequencyString, FrequencyDto::class.java)
        }
        result.exceptionOrNull()?.let { error ->
            mindboxLogE(
                message = "Failed to parse JsonObject: $frequencyString",
                exception = error
            )
        }
        return result.getOrNull()
    }

    override fun deserializeToConfigDtoBlank(inAppConfig: String): InAppConfigResponseBlank? {
        val jsonObject = runCatching {
            JsonParser.parseString(inAppConfig).asJsonObject
        }.getOrElse {
            mindboxLogE("Failed to parse inAppConfig json", it)
            return null
        }

        val inApps = jsonObject.getOrNull("inapps")?.let { deserializeInApps(it) }
        val settings = jsonObject.getOrNull("settings")?.let { deserializeSettings(it) }
        val abtests = jsonObject.getOrNull("abtests")?.let { deserializeAbtests(it) }
        val monitoring = jsonObject.getOrNull("monitoring")?.let { deserializeMonitoring(it) }

        return InAppConfigResponseBlank(inApps, monitoring, settings, abtests)
    }

    internal fun deserializeMonitoring(json: JsonElement?): MonitoringDto? = runCatching {
        MonitoringDto(logs = json!!
            .asJsonObject.get("logs")
            .asJsonArray.mapNotNull { log ->
                runCatching {
                    gson.fromJson(log, LogRequestDtoBlank::class.java)?.copy()
                }.getOrNull {
                    mindboxLogE("Failed to parse logs block", it)
                }
            })
    }.getOrNull {
        mindboxLogE("Failed to parse monitoring block", it)
    }

    internal fun deserializeInApps(json: JsonElement?): List<InAppDtoBlank>? =
        gson.fromJson<List<InAppDtoBlank>>(json)
            .getOrNull {
                mindboxLogE("Failed to parse inapps block", it)
            }

    internal fun deserializeSettings(json: JsonElement?): SettingsDtoBlank? = runCatching {
        json?.let {
            val operations = json.asJsonObject.get("operations")?.let { operationsJson ->
                runCatching {
                    operationsJson.asJsonObject.entrySet().associate { (key, value) ->
                        key to runCatching {
                            gson.fromJson(value, OperationDtoBlank::class.java)?.copy()
                        }.getOrNull {
                            mindboxLogE("Failed to parse operation $key", it)
                        }
                    }.filterValues { it != null }
                }.getOrNull {
                    mindboxLogE("Failed to parse operations block", it)
                }
            }

            val ttl = runCatching {
                gson.fromJson(json.asJsonObject.get("ttl"), TtlDtoBlank::class.java)?.copy()
            }.getOrNull {
                mindboxLogE("Failed to parse ttl block", it)
            }

            val slidingExpiration = runCatching {
                gson.fromJson(json.asJsonObject.get("slidingExpiration"), SlidingExpirationDtoBlank::class.java)?.copy()
            }.getOrNull {
                mindboxLogE("Failed to parse slidingExpiration block")
            }

            SettingsDtoBlank(operations, ttl, slidingExpiration)
        }
    }.getOrNull {
        mindboxLogE("Failed to parse settings block", it)
    }

    internal fun deserializeAbtests(json: JsonElement?): List<ABTestDto>? = runCatching {
        json?.asJsonArray?.map { abtest ->
            gson.fromJson<ABTestDto?>(abtest).getOrThrow()!!
        }?.map { it.copy() }
    }.getOrNull {
        mindboxLogE("Failed to parse abtests block", it)
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
                            is PayloadBlankDto.WebViewBlankDto -> {
                                PayloadDto.WebViewDto(
                                    content = PayloadDto.ModalWindowDto.ContentDto(
                                        background = BackgroundDto(
                                            layers = payloadBlankDto.content?.background?.layers?.mapNotNull {
                                                deserializeToBackgroundLayersDto(it as JsonObject)
                                            }),
                                        elements = payloadBlankDto.content?.elements?.mapNotNull {
                                            deserializeToElementDto(it)
                                        }
                                    ), type = PayloadDto.WebViewDto.WEBVIEW_JSON_NAME
                                )
                            }
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
