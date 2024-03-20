package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal class InAppActionHandler {

    var mindboxView: MindboxView? = null
    fun handle(
        action: InAppAction,
        mindboxView: MindboxView?,

        ): InAppActionResult {
        return LoggingExceptionHandler.runCatching(InAppActionResult("", "", true)) {
            action.execute(mindboxView = mindboxView)
        }
    }

    fun createAction(
        layerAction: Layer.ImageLayer.Action,
    ): InAppAction {
        return when (layerAction) {
            is Layer.ImageLayer.Action.RedirectUrlAction ->
                RedirectUrlInAppAction(url = layerAction.url, payload = layerAction.payload)

            is Layer.ImageLayer.Action.PushPermissionAction -> {
                PushPermissionInAppAction(
                    payload = layerAction.payload
                )
            }
        }
    }
}
