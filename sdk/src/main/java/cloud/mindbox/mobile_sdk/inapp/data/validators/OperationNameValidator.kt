package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.equalsAny
import cloud.mindbox.mobile_sdk.inapp.domain.models.OperationName

internal class OperationNameValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean = item.equalsAny(
        *OperationName.values().map { it.operation }.toTypedArray()
    )
}
