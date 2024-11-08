package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class ModalElementValidator(private val closeButtonElementValidator: CloseButtonModalElementValidator) : Validator<ElementDto?> {
    override fun isValid(item: ElementDto?): Boolean =
        when (item) {
            is ElementDto.CloseButtonElementDto -> {
                closeButtonElementValidator.isValid(item)
            }
            null -> false
        }
}
