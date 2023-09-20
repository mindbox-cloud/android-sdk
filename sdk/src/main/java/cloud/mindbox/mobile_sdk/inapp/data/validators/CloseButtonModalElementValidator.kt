package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class CloseButtonModalElementValidator(private val sizeValidator: CloseButtonModalSizeValidator, private val positionValidator: CloseButtonModalPositionValidator) :
    Validator<ElementDto.CloseButtonElementDto?> {
    override fun isValid(item: ElementDto.CloseButtonElementDto?): Boolean {
        return sizeValidator.isValid(item?.size) && positionValidator.isValid(item?.position)
    }
}