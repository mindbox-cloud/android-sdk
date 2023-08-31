package cloud.mindbox.mobile_sdk.inapp.data.validators

import android.graphics.Color
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.PayloadDto

internal class ModalWindowFormValidator : Validator<PayloadDto.ModalWindowDto> {

    private val actionValidator: ActionValidator = ActionValidator()
    private val sourceValidator: SourceValidator = SourceValidator()
    private val elementValidator: ElementValidator = ElementValidator()

    override fun isValid(item: PayloadDto.ModalWindowDto): Boolean {
        if (item.type != PayloadDto.ModalWindowDto.MODAL_JSON_NAME) return false
        item.content?.background?.layers = item.content?.background?.layers?.filter { layerDto ->
            val imageLayerDto =
                layerDto as? BackgroundDto.LayerDto.ImageLayerDto
                    ?: return@filter false
            imageLayerDto.type == BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME &&
                    actionValidator.isValid(imageLayerDto.action) &&
                    sourceValidator.isValid(imageLayerDto.source)

        }
        if (item.content?.background?.layers.isNullOrEmpty()) return false
        item.content?.elements = item.content?.elements?.mapNotNull { elementDto ->
            if (elementValidator.isValid(elementDto)) {
                elementDto
            } else {
                elementDto?.default()
            }
        }
        return item.content?.elements?.filter { elementDto ->
            elementDto?.validateValues() == true
        }.isNullOrEmpty()
    }

    internal class ElementValidator : Validator<ElementDto?> {

        private val sizeNames = setOf("dp")
        private val marginNames = setOf("proportion")

        override fun isValid(item: ElementDto?): Boolean {
            return when (item) {
                is ElementDto.CloseButtonElementDto -> {
                    item.type == ElementDto.CloseButtonElementDto.CLOSE_BUTTON_ELEMENT_JSON_NAME
                            && item.color != null && runCatching { Color.parseColor(item.color) }.getOrNull() != null
                            && item.lineWidth != null && item.lineWidth.toString()
                        .toDoubleOrNull() != null
                            && marginNames.contains(item.position?.margin?.kind)
                            && sizeNames.contains(item.size?.kind)
                }

                else -> {
                    false
                }
            }
        }
    }

    internal class SourceValidator : Validator<BackgroundDto.LayerDto.ImageLayerDto.SourceDto?> {
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