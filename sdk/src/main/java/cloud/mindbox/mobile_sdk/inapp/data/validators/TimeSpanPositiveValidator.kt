package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class TimeSpanPositiveValidator : Validator<TimeSpan?> {
    override fun isValid(item: TimeSpan?): Boolean =
        loggingRunCatching(false) {
            item?.toMillis()?.let { it > 0 } ?: false
        }
}
