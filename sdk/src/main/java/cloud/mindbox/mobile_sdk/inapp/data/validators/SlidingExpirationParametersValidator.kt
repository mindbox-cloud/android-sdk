package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class SlidingExpirationParametersValidator : Validator<SettingsDtoBlank.SlidingExpirationDtoBlank> {
    override fun isValid(item: SettingsDtoBlank.SlidingExpirationDtoBlank): Boolean =
        loggingRunCatching(false) {
            item.inappSession.parseTimeSpanToMillis() > 0
        }
}
