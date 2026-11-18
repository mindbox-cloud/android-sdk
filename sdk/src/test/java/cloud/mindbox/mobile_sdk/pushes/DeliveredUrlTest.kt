package cloud.mindbox.mobile_sdk.pushes

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
    fun `query parameter is added to the url`() {
        assertEquals("https://mbx.ru/d?dateTimeOffset=0", url("https://mbx.ru/d").withQueryParameter("dateTimeOffset", "0"))
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
