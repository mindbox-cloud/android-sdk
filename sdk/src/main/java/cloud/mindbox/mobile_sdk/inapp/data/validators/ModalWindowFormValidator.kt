package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto

internal class ModalWindowFormValidator(
    private val imageLayerValidator: ImageLayerValidator,
    private val elementValidator: ElementValidator
) : Validator<PayloadDto.ModalWindowDto?> {

    override fun isValid(item: PayloadDto.ModalWindowDto?): Boolean {
        if (item?.type != PayloadDto.ModalWindowDto.MODAL_JSON_NAME) return false
        val layers = item.content?.background?.layers?.filterNotNull()
        if (layers.isNullOrEmpty()) return false
        val invalidLayer = layers.find { layerDto ->
            when (layerDto) {
                is BackgroundDto.LayerDto.ImageLayerDto -> {
                    !imageLayerValidator.isValid(layerDto)
                }
            }
        }
        if (invalidLayer != null) return false
        item.content.elements?.forEach { elementDto ->
            if (!elementValidator.isValid(elementDto)) {
                return false
            }
        }
        return true
    }
}
