package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.domain.models.PayloadDto

internal class SnackbarValidator : Validator<PayloadDto.SnackbarDto?> {
    override fun isValid(item: PayloadDto.SnackbarDto?): Boolean {
        return true
    }
}