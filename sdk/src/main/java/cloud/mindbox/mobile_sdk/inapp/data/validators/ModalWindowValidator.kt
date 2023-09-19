package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal class ModalWindowValidator(
    private val imageLayerValidator: ImageLayerValidator,
    private val elementValidator: ElementValidator
) : Validator<PayloadDto.ModalWindowDto?> {

    override fun isValid(item: PayloadDto.ModalWindowDto?): Boolean {
        if (item?.type != PayloadDto.ModalWindowDto.MODAL_JSON_NAME) {
            mindboxLogD("InApp is not valid. Expected type = ${PayloadDto.ModalWindowDto.MODAL_JSON_NAME}, actual type = ${item?.type}")
            return false
        }
        val layers = item.content?.background?.layers?.filterNotNull()
        if (layers.isNullOrEmpty()) {
            mindboxLogD("InApp is not valid. Layers should not be empty. Layers are = $layers")
            return false
        }
        val invalidLayer = layers.find { layerDto ->
            when (layerDto) {
                is BackgroundDto.LayerDto.ImageLayerDto -> {
                    mindboxLogD("Start checking image layer")
                    val rez = !imageLayerValidator.isValid(layerDto)
                    mindboxLogD("Finish checking image layer and it's validity = $rez")
                    rez
                }
            }
        }
        if (invalidLayer != null) {
            mindboxLogD("InApp is not valid. At least one layer is invalid")
            return false
        }
        item.content.elements?.forEach { elementDto ->
            if (!elementValidator.isValid(elementDto)) {
                mindboxLogD("InApp is not valid. At least one element is invalid")
                return false
            }
        }
        mindboxLogD("Current inApp payload is valid")
        return true
    }
}
