package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

internal class IntegerPositiveValidator : Validator<Int?> {
    override fun isValid(item: Int?): Boolean =
        loggingRunCatching(false) {
            item?.let { it > 0 } ?: false
        }
}
