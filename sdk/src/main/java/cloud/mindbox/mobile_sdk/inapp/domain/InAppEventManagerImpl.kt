package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal class InAppEventManagerImpl : InAppEventManager {

    override fun isValidInAppEvent(event: InAppEventType): Boolean {
        return event is InAppEventType.AppStartup || (event is InAppEventType.OrdinalEvent && (event.eventType is EventType.SyncOperation || event.eventType is EventType.AsyncOperation))
    }
}