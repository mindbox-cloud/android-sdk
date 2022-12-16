package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto

internal interface InAppValidator {

    fun validateInApp(inApp: InAppDto): Boolean
}