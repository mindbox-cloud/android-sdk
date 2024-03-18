package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.InAppActionHandler
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class InAppActionHandlerTest {

    private val mindboxNotificationManager = mockk<MindboxNotificationManager>()

    @Test
    fun `createAction returns RedirectUrlInAppAction for RedirectUrlAction`() {

        val redirectUrlAction =
            Layer.ImageLayer.Action.RedirectUrlAction(url = "test_url", payload = "test_payload")

        val resultAction = InAppActionHandler.createAction(
            layerAction = redirectUrlAction,
            mindboxNotificationManager = mindboxNotificationManager
        )

        Assert.assertTrue(resultAction is RedirectUrlInAppAction)
        resultAction as RedirectUrlInAppAction
        Assert.assertTrue(resultAction.url == "test_url")
        Assert.assertTrue(resultAction.payload == "test_payload")
    }

    @Test
    fun `createAction returns PushPermissionInAppAction for PushPermissionAction`() {

        val pushPermissionAction =
            Layer.ImageLayer.Action.PushPermissionAction(payload = "test_payload")

        val resultAction =
            InAppActionHandler.createAction(pushPermissionAction, mindboxNotificationManager)

        Assert.assertTrue(resultAction is PushPermissionInAppAction)
        resultAction as PushPermissionInAppAction
        Assert.assertTrue(resultAction.payload == "test_payload")
        Assert.assertTrue(resultAction.mindboxNotificationManager == mindboxNotificationManager)
    }
}