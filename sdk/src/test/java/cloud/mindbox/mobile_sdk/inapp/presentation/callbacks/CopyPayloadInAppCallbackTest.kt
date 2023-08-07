package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.ClipboardManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class CopyPayloadInAppCallbackTest {

    private val mockkCallbackInteractor: CallbackInteractor = mockk()

    private val mockClipboardManager: ClipboardManager = mockk()

    private val copyPayloadInAppCallback: CopyPayloadInAppCallback = CopyPayloadInAppCallback()

    @Before
    fun onTestStart() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { clipboardManager } returns mockClipboardManager
            every { callbackInteractor } returns mockkCallbackInteractor
        }
    }

    @Test
    fun `open url called and string should be copied`() {
        val stringToCopy = "String to copy"
        every {
            mockkCallbackInteractor.shouldCopyString(any())
        } returns true
        every {
            mockClipboardManager.copyToClipboard(stringToCopy)
        } just runs
        copyPayloadInAppCallback.onInAppClick("", "", stringToCopy)
        verify(exactly = 1) {
            mockClipboardManager.copyToClipboard(stringToCopy)
        }
    }

    @Test
    fun `open url called and string should not be copied`() {
        val stringToCopy = "String to copy"
        every {
            mockkCallbackInteractor.shouldCopyString(any())
        } returns false
        every {
            mockClipboardManager.copyToClipboard(stringToCopy)
        } just runs
        copyPayloadInAppCallback.onInAppClick("", stringToCopy, "")
        verify(exactly = 0) {
            mockClipboardManager.copyToClipboard(stringToCopy)
        }
    }
}