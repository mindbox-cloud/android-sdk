package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.enumValue
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTime
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal class TtlParametersValidator : Validator<SettingsDtoBlank.TtlParametersDtoBlank> {

    override fun isValid(item: SettingsDtoBlank.TtlParametersDtoBlank): Boolean =
        unitIsValid(item) && valueIsValid(item)

    private fun unitIsValid(ttlParameters: SettingsDtoBlank.TtlParametersDtoBlank): Boolean {
        return LoggingExceptionHandler.runCatching(defaultValue = false) {
            ttlParameters.unit?.enumValue<InAppTime>() != null
        }
    }

    private fun valueIsValid(ttlParameters: SettingsDtoBlank.TtlParametersDtoBlank?): Boolean {
        return ttlParameters?.value?.let { it >= 0 } ?: false
    }
}