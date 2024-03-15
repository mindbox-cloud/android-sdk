package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.app.Activity
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal object InAppActionHandler {
    fun handleAction(
        action: InAppAction,
        activity: Activity,
        callback: (InAppActionResult) -> Unit
    ) {
        LoggingExceptionHandler.runCatching {
            action.execute(activity, callback)
        }
    }
}