package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.InAppActionHandler
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class InAppActionHandlerTest {

    private lateinit var inAppActionHandler: InAppActionHandler

    @Before
    fun setUp() {
        inAppActionHandler = InAppActionHandler()
    }

    @Test
    fun `createAction returns RedirectUrlInAppAction for RedirectUrlAction`() {

        val redirectUrlAction =
            Layer.ImageLayer.Action.RedirectUrlAction(url = "test_url", payload = "test_payload")

        val resultAction = inAppActionHandler.createAction(
            layerAction = redirectUrlAction
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
            inAppActionHandler.createAction(pushPermissionAction)

        Assert.assertTrue(resultAction is PushPermissionInAppAction)
        resultAction as PushPermissionInAppAction
        Assert.assertTrue(resultAction.payload == "test_payload")
    }
}