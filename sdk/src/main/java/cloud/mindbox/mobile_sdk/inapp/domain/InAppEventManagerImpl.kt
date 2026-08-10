package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType

internal class InAppEventManagerImpl : InAppEventManager {

    override fun isValidInAppEvent(event: InAppEventType): Boolean {
        val isAppStartUp = event is InAppEventType.AppStartup
        // An embedded place asking for content is a trigger like any other: the config decides
        // what goes into the place, and it is matched by the operation name of this very event.
        val isEmbeddedPlaceRequested = event is InAppEventType.EmbeddedPlaceRequested
        val isOrdinalEvent =
            event is InAppEventType.OrdinalEvent && (event.eventType is EventType.SyncOperation || event.eventType is EventType.AsyncOperation)
        val isNotInAppEvent = (listOf(
            MindboxEventManager.IN_APP_OPERATION_VIEW_TYPE,
            MindboxEventManager.IN_APP_OPERATION_TARGETING_TYPE,
            MindboxEventManager.IN_APP_OPERATION_CLICK_TYPE,
            MindboxEventManager.IN_APP_OPERATION_SHOW_FAILURE_TYPE
        ).contains(event.name).not())
        return isAppStartUp ||
            isEmbeddedPlaceRequested ||
            (isOrdinalEvent &&
                isNotInAppEvent)
    }
}
