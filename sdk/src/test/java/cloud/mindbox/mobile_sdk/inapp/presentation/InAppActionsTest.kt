package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class InAppActionsTest {

    private lateinit var mindboxView: MindboxView

    @Before
    fun setUp() {
        mindboxView = mockk<MindboxView>(relaxed = true)
    }

    @Test
    fun `execute of RedirectUrlInAppAction return correct data`() {
        val url = "https://test.url"
        val payload = "testPayload"
        val action = RedirectUrlInAppAction(url, payload)

        val data = action.execute(mindboxView)

        Assert.assertEquals(url, data.redirectUrl)
        Assert.assertEquals(payload, data.payload)
        Assert.assertEquals(true, data.shouldDismiss)
    }

    @Test
    fun `execute of RedirectUrlInAppAction return data with isNeedDismiss false`() {
        val url = ""
        val payload = ""
        val action = RedirectUrlInAppAction(url, payload)

        val data = action.execute(mindboxView)

        Assert.assertEquals(url, data.redirectUrl)
        Assert.assertEquals(payload, data.payload)
        Assert.assertEquals(false, data.shouldDismiss)
    }

    @Test
    fun `execute of PushPermissionInAppAction triggers permission request and return data`() {
        val payload = "testPayload"
        val action = PushPermissionInAppAction(payload)

        val data = action.execute(mindboxView)

        Assert.assertEquals("", data.redirectUrl)
        Assert.assertEquals(payload, data.payload)
        Assert.assertEquals(true, data.shouldDismiss)
    }
}
