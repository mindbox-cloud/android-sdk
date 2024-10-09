package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal interface InAppAction {
    fun execute(mindboxView: MindboxView?): InAppActionResult
}

internal class RedirectUrlInAppAction(val url: String, val payload: String) : InAppAction {
    override fun execute(mindboxView: MindboxView?): InAppActionResult {
        return InAppActionResult(
            redirectUrl = url,
            payload = payload,
            shouldDismiss = shouldDismiss()
        )
    }

    private fun shouldDismiss(): Boolean {
        return url.isNotBlank() || payload.isNotBlank()
    }
}

internal class PushPermissionInAppAction(
    val payload: String
) : InAppAction {

    override fun execute(mindboxView: MindboxView?): InAppActionResult {
        mindboxLogI("In-app for push activation was clicked")
        return InAppActionResult(
            redirectUrl = "",
            payload = payload,
            shouldDismiss = shouldDismiss(),
            onCompleted = { mindboxView?.requestPermission() }
        )
    }

    private fun shouldDismiss(): Boolean {
        return true
    }
}
