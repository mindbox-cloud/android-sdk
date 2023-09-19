package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto.LayerDto.ImageLayerDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal class ImageLayerValidator : Validator<ImageLayerDto?> {

    private val actionValidator: ActionValidator =
        ActionValidator()
    private val sourceValidator: SourceValidator =
        SourceValidator()

    override fun isValid(item: ImageLayerDto?): Boolean {
        val actionRez = actionValidator.isValid(item?.action)
        val sourceRez = sourceValidator.isValid(item?.source)
        val rez = item?.type == ImageLayerDto.IMAGE_TYPE_JSON_NAME &&
                actionRez && sourceRez
        if (!rez) {
            mindboxLogD("InApp is invalid. Image layer is expected to have valid action, valid source and type = ${ImageLayerDto.IMAGE_TYPE_JSON_NAME}. " +
                    "Actual image layer is ${item?.type} with action validity = $actionRez and souceValidity $sourceRez")
        }
        return rez
    }

    internal class SourceValidator :
        Validator<ImageLayerDto.SourceDto?> {
        override fun isValid(item: ImageLayerDto.SourceDto?): Boolean {
            return when (item) {
                is ImageLayerDto.SourceDto.UrlSourceDto -> {
                    val rez = item.type == ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME
                            && item.value != null
                    if (!rez) {
                        mindboxLogD(
                            "InApp is not valid. Image layer source is expected to have type = ${ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME}," +
                                    " non-null value Actual imageLayer source type = ${item.type}, value = ${item.value}"
                        )
                    }
                    rez
                }
                else -> {
                    mindboxLogD("Unknown action. Should never trigger. Otherwise the deserialization is broken")
                    false
                }
            }
        }
    }

    internal class ActionValidator :
        Validator<ImageLayerDto.ActionDto?> {
        override fun isValid(item: ImageLayerDto.ActionDto?): Boolean {
            return when {
                (item is ImageLayerDto.ActionDto.RedirectUrlActionDto) -> {
                    val rez =
                        item.type == ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME
                                && item.value != null && item.intentPayload != null
                    if (!rez) {
                        mindboxLogD(
                            "InApp is not valid. Image layer action is expected to have type = ${ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME}," +
                                    " non-null value and non-null intentPayload. Actual imageLayer action type = ${item.type}, value = ${item.value}, intentPayload = ${item.intentPayload}"
                        )
                    }
                    rez
                }

                else -> {
                    mindboxLogD("Unknown action. Should never trigger. Otherwise the deserialization is broken")
                    false
                }
            }
        }
    }
}