package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto

internal interface InAppValidator {

    fun validateInApp(inApp: InAppDto): Boolean

    fun validateInAppVersion(inAppDto: InAppConfigResponseBlank.InAppDtoBlank): Boolean
}