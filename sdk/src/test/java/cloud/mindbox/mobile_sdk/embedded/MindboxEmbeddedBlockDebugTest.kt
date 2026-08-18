package cloud.mindbox.mobile_sdk.embedded

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockDebugTest {

    @After
    fun tearDown() {
        // The overrides are process-wide: a leftover would decide the next test's answer.
        MindboxEmbeddedBlockDebug.removeAllContent()
    }

    @Test
    fun `a set override is what the factory reads`() {
        val content = MindboxEmbeddedBlockDebug.Content.Html("<html></html>")

        MindboxEmbeddedBlockDebug.setContent(content, "place")

        assertEquals(content, EmbeddedBlockContentOverrides.contentFor("place"))
    }

    @Test
    fun `a place without an override has none`() {
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Empty, "place")

        assertNull(EmbeddedBlockContentOverrides.contentFor("other-place"))
    }

    @Test
    fun `setting again replaces the override`() {
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Empty, "place")
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Url("https://mindbox.ru"), "place")

        assertEquals(
            MindboxEmbeddedBlockDebug.Content.Url("https://mindbox.ru"),
            EmbeddedBlockContentOverrides.contentFor("place"),
        )
    }

    @Test
    fun `removing an override gives the place its config content back`() {
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Empty, "place")

        MindboxEmbeddedBlockDebug.removeContent("place")

        assertNull(EmbeddedBlockContentOverrides.contentFor("place"))
    }

    @Test
    fun `removing a place that was never overridden is not an error`() {
        MindboxEmbeddedBlockDebug.removeContent("never-set")

        assertNull(EmbeddedBlockContentOverrides.contentFor("never-set"))
    }

    @Test
    fun `removing all drops every place at once`() {
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Empty, "first")
        MindboxEmbeddedBlockDebug.setContent(MindboxEmbeddedBlockDebug.Content.Empty, "second")

        MindboxEmbeddedBlockDebug.removeAllContent()

        assertNull(EmbeddedBlockContentOverrides.contentFor("first"))
        assertNull(EmbeddedBlockContentOverrides.contentFor("second"))
    }
}
