package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.InAppEventType

internal interface InAppEventManager {

    fun isValidInAppEvent(event: InAppEventType): Boolean
}
