package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto

internal class OperationValidator : Validator<SettingsDto.OperationDtoBlank?> {

    override fun isValid(item: SettingsDto.OperationDtoBlank?): Boolean =
        !item?.systemName.isNullOrBlank()

}