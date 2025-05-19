package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class TimeSpanPositiveValidator : Validator<String?> {
    override fun isValid(item: String?): Boolean =
        loggingRunCatching(false) {
            item?.parseTimeSpanToMillis()?.let { it > 0 } ?: false
        }
}
