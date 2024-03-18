package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.InAppActionResult
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class InAppActionsTest {
    @Test
    fun `execute of RedirectUrlInAppAction calls callback with correct result`() {
        val activity = mockk<Activity>(relaxed = true)
        val url = "https://test.url"
        val payload = "testPayload"
        val action = RedirectUrlInAppAction(url, payload)
        val callback = mockk<(InAppActionResult) -> Unit>(relaxed = true)

        action.execute(activity, callback)

        verify(exactly = 1) {
            callback(
                InAppActionResult(
                    redirectUrl = url,
                    payload = payload,
                    isNeedDismiss = true
                )
            )
        }
    }

    @Test
    fun `execute of RedirectUrlInAppAction calls callback with isNeedDismiss false`() {
        val activity = mockk<Activity>(relaxed = true)
        val url = ""
        val payload = ""
        val action = RedirectUrlInAppAction(url, payload)
        val callback = mockk<(InAppActionResult) -> Unit>(relaxed = true)

        action.execute(activity, callback)

        verify(exactly = 1) {
            callback(
                InAppActionResult(
                    redirectUrl = url,
                    payload = payload,
                    isNeedDismiss = false
                )
            )
        }
    }

    @Test
    fun `execute of PushPermissionInAppAction triggers permission request and calls callback`() {
        val activity = mockk<Activity>(relaxed = true)
        val mindboxNotificationManager = mockk<MindboxNotificationManager>(relaxed = true)
        val payload = "testPayload"
        val action = PushPermissionInAppAction(payload, mindboxNotificationManager)
        val callback = mockk<(InAppActionResult) -> Unit>(relaxed = true)

        action.execute(activity, callback)

        verify { mindboxNotificationManager.requestPermission(activity) }
        verify {
            callback(
                InAppActionResult(
                    redirectUrl = "",
                    payload = payload,
                    isNeedDismiss = true
                )
            )
        }
    }
}