package cloud.mindbox.mobile_sdk.inapp.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardManagerTest {

    @get:Rule
    val rule = MockKRule(this)

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var androidClipboardManager: ClipboardManager

    @OverrideMockKs
    private lateinit var clipboardManager: ClipboardManagerImpl

    @Test
    fun copyToClipboard_shouldSetPrimaryClipWithCorrectData() {
        val copyString = "Test String"
        val clipDataSlot = slot<ClipData>()
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns androidClipboardManager
        every { androidClipboardManager.setPrimaryClip(capture(clipDataSlot)) } just runs

        clipboardManager.copyToClipboard(copyString)

        val expectedClipData = ClipData.newPlainText("payload", copyString)
        assertTrue(clipDataSlot.isCaptured)
        assertEquals(clipDataSlot.captured.getItemAt(0).text, expectedClipData.getItemAt(0).text)
    }
}
