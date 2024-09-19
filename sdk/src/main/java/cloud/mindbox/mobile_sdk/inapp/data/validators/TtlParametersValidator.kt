package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class TtlParametersValidator : Validator<SettingsDtoBlank.TtlDtoBlank> {

    override fun isValid(item: SettingsDtoBlank.TtlDtoBlank): Boolean = loggingRunCatching(false) {
        item.inApps.parseTimeSpanToMillis() >= 0
    }
}