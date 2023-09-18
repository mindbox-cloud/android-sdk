package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto

internal class ImageLayerValidator : Validator<BackgroundDto.LayerDto.ImageLayerDto?> {

    private val actionValidator: ActionValidator =
        ActionValidator()
    private val sourceValidator: SourceValidator =
        SourceValidator()

    override fun isValid(item: BackgroundDto.LayerDto.ImageLayerDto?): Boolean {
        return item?.type == BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME &&
                actionValidator.isValid(item.action) &&
                sourceValidator.isValid(item.source)
    }

    internal class SourceValidator :
        Validator<BackgroundDto.LayerDto.ImageLayerDto.SourceDto?> {
        override fun isValid(item: BackgroundDto.LayerDto.ImageLayerDto.SourceDto?): Boolean {
            return when (item) {
                is BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto -> {
                    item.type == BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME
                            && item.value != null
                }

                else -> {
                    false
                }
            }
        }
    }

    internal class ActionValidator :
        Validator<BackgroundDto.LayerDto.ImageLayerDto.ActionDto?> {
        override fun isValid(item: BackgroundDto.LayerDto.ImageLayerDto.ActionDto?): Boolean {
            return when {
                (item is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto) -> {
                    item.type == BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME
                            && item.value != null && item.intentPayload != null
                }
                else -> {
                    false
                }
            }
        }
    }
}