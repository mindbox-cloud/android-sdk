package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal interface InAppAction {
    fun execute(activity: Activity, callback: (InAppActionResult) -> Unit)
}

internal class RedirectUrlInAppAction(val url: String, val payload: String) : InAppAction {
    override fun execute(activity: Activity, callback: (InAppActionResult) -> Unit) {
        callback(
            InAppActionResult(
                redirectUrl = url,
                payload = payload,
                isNeedDismiss = isNeedDismiss()
            )
        )
    }

    private fun isNeedDismiss(): Boolean {
        return url.isNotBlank() || payload.isNotBlank()
    }
}

internal class PushPermissionInAppAction(
    val payload: String,
    val mindboxNotificationManager: MindboxNotificationManager
) : InAppAction {

    override fun execute(activity: Activity, callback: (InAppActionResult) -> Unit) {
        mindboxLogI("In-app for push activation was clicked")
        mindboxNotificationManager.requestPermission(activity = activity)
        callback(
            InAppActionResult(
                redirectUrl = "",
                payload = payload,
                isNeedDismiss = isNeedDismiss()
            )
        )
    }

    private fun isNeedDismiss(): Boolean {
        return true
    }
}