package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class SnackBarElementValidator(private val closeButtonElementValidator: CloseButtonSnackbarElementValidator) :
    Validator<ElementDto?> {
    override fun isValid(item: ElementDto?): Boolean {
        return when (item) {
            is ElementDto.CloseButtonElementDto -> {
                closeButtonElementValidator.isValid(item)
            }

            null -> false
        }
    }
}
