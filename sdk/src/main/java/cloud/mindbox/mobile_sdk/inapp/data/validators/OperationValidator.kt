package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank

internal class OperationValidator : Validator<SettingsDtoBlank.OperationDtoBlank?> {

    override fun isValid(item: SettingsDtoBlank.OperationDtoBlank?): Boolean =
        !item?.systemName.isNullOrBlank()

}