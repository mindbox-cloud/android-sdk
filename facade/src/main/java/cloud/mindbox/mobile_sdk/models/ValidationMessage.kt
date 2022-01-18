package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk_core.models.ValidationMessageInternal

data class ValidationMessage(
    val message: String? = null,
    val location: String? = null
) {

    companion object {

        internal fun fromInternal(validationMessage: ValidationMessageInternal) = ValidationMessage(
            message = validationMessage.message,
            location = validationMessage.location
        )

    }

}
