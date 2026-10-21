package cloud.mindbox.mobile_sdk.inapp.presentation.view

import org.junit.Assert.assertEquals
import org.junit.Test

internal class WebViewInitTimeoutDescriptionTest {

    @Test
    fun `a page that never sent init is described as on iOS`() {
        assertEquals("Page did not send init within 7s.", WebViewInAppViewHolder.initTimeoutDescription(readyCheckFailure = null))
    }

    @Test
    fun `a loaded page without the JS bridge is described as on iOS with the ready check status`() {
        assertEquals(
            "JS bridge not found on the loaded page within 7s: no-native-bridge.",
            WebViewInAppViewHolder.initTimeoutDescription(readyCheckFailure = "no-native-bridge")
        )
    }
}
