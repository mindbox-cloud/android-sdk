package cloud.mindbox.mobile_sdk.inapp.data.validators

import android.graphics.Color
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto.ModalWindowDto.ContentDto.BackgroundDto.LayerDto.ImageLayerDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto.ModalWindowDto.ContentDto.ElementDto

internal class ModalWindowFormValidator : Validator<PayloadDto.ModalWindowDto> {

    private val actionValidator: ActionValidator = ActionValidator()
    private val sourceValidator: SourceValidator = SourceValidator()
    private val elementValidator: ElementValidator = ElementValidator()

    override fun isValid(item: PayloadDto.ModalWindowDto): Boolean {
        if (item.type != PayloadDto.ModalWindowDto.MODAL_JSON_NAME) return false
        item.content?.background?.layers = item.content?.background?.layers?.filter { layerDto ->
            val imageLayerDto =
                layerDto as? ImageLayerDto
                    ?: return@filter false
            imageLayerDto.type == ImageLayerDto.IMAGE_TYPE_JSON_NAME &&
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
        return true
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
                            && isValidPosition(item.position)
                            && isValidSize(item.size)
                }

                else -> {
                    false
                }
            }
        }

        private fun isValidSize(item: ElementDto.CloseButtonElementDto.SizeDto?): Boolean {
            if (item?.kind == null) return false
            return sizeNames.contains(item.kind) && item.width != null && item.height != null
        }

        private fun isValidPosition(item: ElementDto.CloseButtonElementDto.PositionDto?): Boolean {
            if (item?.margin?.kind == null) return false
            return marginNames.contains(item.margin.kind)
        }


    }

    internal class SourceValidator : Validator<ImageLayerDto.SourceDto?> {
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