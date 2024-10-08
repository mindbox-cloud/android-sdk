package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class CloseButtonSnackbarElementValidator(
    private val positionValidator: CloseButtonSnackbarPositionValidator,
    private val sizeValidator: CloseButtonSnackbarSizeValidator
) : Validator<ElementDto.CloseButtonElementDto?> {
    override fun isValid(item: ElementDto.CloseButtonElementDto?): Boolean =
        sizeValidator.isValid(item?.size) && positionValidator.isValid(item?.position)
}
