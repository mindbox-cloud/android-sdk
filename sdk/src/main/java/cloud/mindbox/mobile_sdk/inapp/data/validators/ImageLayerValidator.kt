package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto.*
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto.LayerDto.*

internal class ImageLayerValidator : Validator<ImageLayerDto?> {

    private val actionValidator: ActionValidator =
        ActionValidator()
    private val sourceValidator: SourceValidator =
        SourceValidator()

    override fun isValid(item: ImageLayerDto?): Boolean {
        return item?.type == ImageLayerDto.IMAGE_TYPE_JSON_NAME &&
                actionValidator.isValid(item.action) &&
                sourceValidator.isValid(item.source)
    }

    internal class SourceValidator :
        Validator<ImageLayerDto.SourceDto?> {
        override fun isValid(item: ImageLayerDto.SourceDto?): Boolean {
            return when (item) {
                is ImageLayerDto.SourceDto.UrlSourceDto -> {
                    item.type == ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME
                            && item.value != null
                }

                else -> {
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
                    item.type == ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME
                            && item.value != null && item.intentPayload != null
                }
                else -> {
                    false
                }
            }
        }
    }
}