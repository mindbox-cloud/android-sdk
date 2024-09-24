package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.InAppActionHandler
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class InAppActionHandlerTest<InAppAction> {

    private lateinit var inAppActionHandler: InAppActionHandler

    @Before
    fun setUp() {
        inAppActionHandler = InAppActionHandler()
    }

    @Test
    fun `createAction returns RedirectUrlInAppAction for RedirectUrlAction`() {

        val resultAction = callPrivateMethod<InAppAction>(
            inAppActionHandler,
            "createAction",
            arrayOf(Class.forName("cloud.mindbox.mobile_sdk.inapp.domain.models.Layer\$ImageLayer\$Action")),
            arrayOf(Layer.ImageLayer.Action.RedirectUrlAction("test_url", "test_payload"))
        )

        Assert.assertTrue(resultAction is RedirectUrlInAppAction)
        resultAction as RedirectUrlInAppAction
        Assert.assertTrue(resultAction.url == "test_url")
        Assert.assertTrue(resultAction.payload == "test_payload")
    }



    @Test
    fun `createAction returns PushPermissionInAppAction for PushPermissionAction`() {

        val resultAction = callPrivateMethod<InAppAction>(
            inAppActionHandler,
            "createAction",
            arrayOf(Class.forName("cloud.mindbox.mobile_sdk.inapp.domain.models.Layer\$ImageLayer\$Action")),
            arrayOf(Layer.ImageLayer.Action.PushPermissionAction("test_payload"))
        )

        Assert.assertTrue(resultAction is PushPermissionInAppAction)
        resultAction as PushPermissionInAppAction
        Assert.assertTrue(resultAction.payload == "test_payload")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> callPrivateMethod(
        instance: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any>
    ): R {
        val method = instance::class.java.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        return method.invoke(instance, *arguments) as R
    }
}