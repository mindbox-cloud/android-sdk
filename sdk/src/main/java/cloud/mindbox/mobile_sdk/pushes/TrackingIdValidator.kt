package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.inapp.data.validators.Validator

internal object TrackingIdValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean {
        if (item.isNullOrBlank()) return false
        return item.any { character -> character != '0' && character != '-' }
    }
}
