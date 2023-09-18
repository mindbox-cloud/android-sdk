package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto

internal class ElementValidator : Validator<ElementDto?> {
    override fun isValid(item: ElementDto?): Boolean {
        return item?.validateValues() ?: false
    }
}