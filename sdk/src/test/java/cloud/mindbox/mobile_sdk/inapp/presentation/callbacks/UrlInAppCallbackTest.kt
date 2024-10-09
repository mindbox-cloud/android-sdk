package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class UrlInAppCallbackTest {

    private val urlInAppCallback: UrlInAppCallback = UrlInAppCallback()

    private val mockActivityManager: ActivityManager = mockk()

    @Before
    fun onTestStart() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { activityManager } returns mockActivityManager
        }
    }

    @Test
    fun `open url called`() {
        val url = "https://www.example.com"
        every {
            mockActivityManager.tryOpenUrl(url)
        } returns true
        urlInAppCallback.onInAppClick("", url, "")
        verify(exactly = 1) {
            mockActivityManager.tryOpenUrl(url)
        }
    }
}
