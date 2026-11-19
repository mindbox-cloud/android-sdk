package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.unmockkObject
import io.mockk.verify
import java.net.URISyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveredUrlTest {

    @Test
    fun `https url is accepted with its own query`() {
        assertEquals("https://mbx.ru/d/key/hash?sig=1", DeliveredUrl.of("https://mbx.ru/d/key/hash?sig=1")?.value)
    }

    @Test
    fun `scheme is matched case-insensitively and surrounding spaces are trimmed`() {
        assertEquals("HTTPS://mbx.ru/d", DeliveredUrl.of("  HTTPS://mbx.ru/d ")?.value)
    }

    @Test
    fun `url that is missing, not https or has no host is rejected`() {
        listOf(
            null,
            "",
            "http://mbx.ru/d",
            "ftp://mbx.ru/d",
            "javascript:alert(1)",
            "mbx.ru/d",
            "https:///d",
            "https://mbx.ru/d with spaces",
        ).forEach { raw ->
            assertNull(raw, DeliveredUrl.of(raw))
        }
    }

    @Test
    fun `unparsable url is reported as a warning, not an error`() {
        mockLogger()
        try {
            assertNull(DeliveredUrl.of("https://mbx.ru/d with spaces"))

            verify(exactly = 1) { MindboxLoggerImpl.w(any(), match { "https://mbx.ru/d with spaces" in it }) }
            verify(exactly = 0) { MindboxLoggerImpl.e(any(), any(), ofType<URISyntaxException>()) }
        } finally {
            unmockkObject(MindboxLoggerImpl)
        }
    }

    @Test
    fun `query parameter is added to the url`() {
        assertEquals("https://mbx.ru/d?dateTimeOffset=0", url("https://mbx.ru/d").withQueryParameter("dateTimeOffset", "0"))
    }

    @Test
    fun `query parameter goes before the fragment`() {
        assertEquals(
            "https://mbx.ru/d?sig=1&dateTimeOffset=0#frag",
            url("https://mbx.ru/d?sig=1#frag").withQueryParameter("dateTimeOffset", "0"),
        )
        assertEquals(
            "https://mbx.ru/d?dateTimeOffset=0#frag?x=1",
            url("https://mbx.ru/d#frag?x=1").withQueryParameter("dateTimeOffset", "0"),
        )
    }

    @Test
    fun `query parameter replaces one already in the url`() {
        assertEquals(
            "https://mbx.ru/d?sig=1&dateTimeOffset=0",
            url("https://mbx.ru/d?dateTimeOffset=5&sig=1").withQueryParameter("dateTimeOffset", "0"),
        )
    }

    @Test
    fun `only the parameter with the same name is replaced and the rest stays as it came`() {
        mapOf(
            "https://mbx.ru/d?dateTimeOffsetX=1&sig=1" to "https://mbx.ru/d?dateTimeOffsetX=1&sig=1&dateTimeOffset=0",
            "https://mbx.ru/d?dateTimeOffset&sig=1" to "https://mbx.ru/d?sig=1&dateTimeOffset=0",
            "https://mbx.ru/d?dateTimeOffset=1&sig=1&dateTimeOffset=2" to "https://mbx.ru/d?sig=1&dateTimeOffset=0",
            "https://mbx.ru/d?dateTimeOffset=5&sig=1#frag" to "https://mbx.ru/d?sig=1&dateTimeOffset=0#frag",
            "https://mbx.ru/d?a=1&&b=a+b%2F&dateTimeOffset=5" to "https://mbx.ru/d?a=1&&b=a+b%2F&dateTimeOffset=0",
            "https://mbx.ru" to "https://mbx.ru?dateTimeOffset=0",
        ).forEach { (raw, expected) ->
            assertEquals(raw, expected, url(raw).withQueryParameter("dateTimeOffset", "0"))
        }
    }

    @Test
    fun `query parameter is the whole query when the url ends with a question mark`() {
        assertEquals("https://mbx.ru/d?dateTimeOffset=0", url("https://mbx.ru/d?").withQueryParameter("dateTimeOffset", "0"))
    }

    @Test
    fun `query of the url from the push is kept`() {
        assertEquals(
            "https://mbx.ru/d?sig=a%2Fb&dateTimeOffset=0",
            url("https://mbx.ru/d?sig=a%2Fb").withQueryParameter("dateTimeOffset", "0"),
        )
    }

    private fun url(raw: String) = checkNotNull(DeliveredUrl.of(raw))
}
