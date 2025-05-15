package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank

internal class SlidingExpirationParametersValidator : Validator<SettingsDtoBlank.SlidingExpirationDtoBlank> {
    override fun isValid(item: SettingsDtoBlank.SlidingExpirationDtoBlank): Boolean = true
}
