package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal object InAppActionHandler {
    fun handle(
        action: InAppAction,
        activity: Activity?,
        callback: (InAppActionResult) -> Unit
    ) {
        LoggingExceptionHandler.runCatching {
            action.execute(activity = activity, callback = callback)
        }
    }

    fun createAction(
        layerAction: Layer.ImageLayer.Action,
        mindboxNotificationManager: MindboxNotificationManager
    ): InAppAction {
        return when (layerAction) {
            is Layer.ImageLayer.Action.RedirectUrlAction ->
                RedirectUrlInAppAction(url = layerAction.url, payload = layerAction.payload)

            is Layer.ImageLayer.Action.PushPermissionAction -> {
                PushPermissionInAppAction(
                    payload = layerAction.payload,
                    mindboxNotificationManager = mindboxNotificationManager
                )
            }
        }
    }
}
