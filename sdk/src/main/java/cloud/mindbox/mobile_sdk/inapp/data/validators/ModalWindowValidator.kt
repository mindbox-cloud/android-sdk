package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class ModalWindowValidator(
    private val imageLayerValidator: ImageLayerValidator,
    private val elementValidator: ModalElementValidator
) : Validator<PayloadDto.ModalWindowDto?> {

    override fun isValid(item: PayloadDto.ModalWindowDto?): Boolean {
        if (item?.type != PayloadDto.ModalWindowDto.MODAL_JSON_NAME) {
            mindboxLogI("InApp is not valid. Expected type = ${PayloadDto.ModalWindowDto.MODAL_JSON_NAME}, actual type = ${item?.type}")
            return false
        }
        val layers = item.content?.background?.layers?.filterNotNull()
        if (layers.isNullOrEmpty()) {
            mindboxLogI("InApp is not valid. Layers should not be empty. Layers are = $layers")
            return false
        }
        val invalidLayer = layers.find { layerDto ->
            when (layerDto) {
                is BackgroundDto.LayerDto.ImageLayerDto -> {
                    mindboxLogI("Start checking image layer")
                    val rez = !imageLayerValidator.isValid(layerDto)
                    mindboxLogI("Finish checking image layer and it's validity = $rez")
                    rez
                }
            }
        }
        if (invalidLayer != null) {
            mindboxLogI("InApp is not valid. At least one layer is invalid")
            return false
        }
        item.content.elements?.forEach { elementDto ->
            if (!elementValidator.isValid(elementDto)) {
                mindboxLogI("InApp is not valid. At least one element is invalid")
                return false
            }
        }
        mindboxLogI("Current inApp payload is valid")
        return true
    }
}
