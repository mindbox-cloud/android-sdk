package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class DeepLinkInAppCallbackTest {

    private val deepLinkInAppCallback: DeepLinkInAppCallback = DeepLinkInAppCallback()

    private val mockActivityManager: ActivityManager = mockk()

    @Before
    fun onTestStart() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { activityManager } returns mockActivityManager
        }
    }

    @Test
    fun `open deeplink called`() {
        val url = "app://page"
        every {
            mockActivityManager.tryOpenDeepLink(url)
        } returns true
        deepLinkInAppCallback.onInAppClick("", url, "")
        verify(exactly = 1) {
            mockActivityManager.tryOpenDeepLink(url)
        }
    }
}
