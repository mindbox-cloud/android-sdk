package cloud.mindbox.mobile_sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class SdkValidationDomainTest {

    // region extractHost

    @Test
    fun `extractHost bare host unchanged`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("api.mindbox.ru"))
    }

    @Test
    fun `extractHost strips https scheme`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("https://api.mindbox.ru"))
    }

    @Test
    fun `extractHost strips http scheme`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("http://api.mindbox.ru"))
    }

    @Test
    fun `extractHost strips trailing slash`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("api.mindbox.ru/"))
    }

    @Test
    fun `extractHost strips https scheme and trailing slash`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("https://api.mindbox.ru/"))
    }

    @Test
    fun `extractHost trims surrounding whitespace`() {
        assertEquals("api.mindbox.ru", SdkValidation.extractHost("  api.mindbox.ru  "))
    }

    // endregion

    // region toBaseUrl

    @Test
    fun `toBaseUrl adds https when no scheme`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("api.mindbox.ru"))
    }

    @Test
    fun `toBaseUrl preserves https scheme`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("https://api.mindbox.ru"))
    }

    @Test
    fun `toBaseUrl preserves http scheme`() {
        assertEquals("http://internal-proxy.com", SdkValidation.toBaseUrl("http://internal-proxy.com"))
    }

    @Test
    fun `toBaseUrl strips trailing slash when scheme present`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("https://api.mindbox.ru/"))
    }

    @Test
    fun `toBaseUrl strips trailing slash when no scheme`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("api.mindbox.ru/"))
    }

    @Test
    fun `toBaseUrl preserves http scheme and strips trailing slash`() {
        assertEquals("http://proxy.internal", SdkValidation.toBaseUrl("http://proxy.internal/"))
    }

    @Test
    fun `toBaseUrl trims surrounding whitespace before adding scheme`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("  api.mindbox.ru  "))
    }

    @Test
    fun `toBaseUrl trims surrounding whitespace when scheme present`() {
        assertEquals("https://api.mindbox.ru", SdkValidation.toBaseUrl("  https://api.mindbox.ru  "))
    }

    @Test
    fun `toBaseUrl preserves path prefix when no scheme`() {
        assertEquals("https://domain.com/api/v2", SdkValidation.toBaseUrl("domain.com/api/v2"))
    }

    @Test
    fun `toBaseUrl preserves path prefix and scheme`() {
        assertEquals(
            "https://api-v2.letu.ru/api/mindbox-regular",
            SdkValidation.toBaseUrl("https://api-v2.letu.ru/api/mindbox-regular")
        )
    }

    @Test
    fun `toBaseUrl strips trailing slash after path prefix`() {
        assertEquals("https://domain.com/api/v2", SdkValidation.toBaseUrl("https://domain.com/api/v2/"))
    }

    // endregion

    // region isValidDomain

    @Test
    fun `isValidDomain accepts bare host`() {
        assertEquals(true, SdkValidation.isValidDomain("api.mindbox.ru"))
    }

    @Test
    fun `isValidDomain accepts https scheme`() {
        assertEquals(true, SdkValidation.isValidDomain("https://api.mindbox.ru"))
    }

    @Test
    fun `isValidDomain accepts https scheme with trailing slash`() {
        assertEquals(true, SdkValidation.isValidDomain("https://api.mindbox.ru/"))
    }

    @Test
    fun `isValidDomain accepts bare host with trailing slash`() {
        assertEquals(true, SdkValidation.isValidDomain("api.mindbox.ru/"))
    }

    @Test
    fun `isValidDomain rejects blank string`() {
        assertEquals(false, SdkValidation.isValidDomain(""))
    }

    @Test
    fun `isValidDomain rejects string with spaces`() {
        assertEquals(false, SdkValidation.isValidDomain("not a domain"))
    }

    @Test
    fun `isValidDomain rejects host with path prefix — domain stays host-only`() {
        assertEquals(false, SdkValidation.isValidDomain("api.mindbox.ru/api/v2"))
    }

    // endregion

    // region isValidOperationsDomain

    @Test
    fun `isValidOperationsDomain accepts bare host`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("api.mindbox.ru"))
    }

    @Test
    fun `isValidOperationsDomain accepts https scheme with trailing slash`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("https://api.mindbox.ru/"))
    }

    @Test
    fun `isValidOperationsDomain accepts host with path prefix`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("domain.com/api/v2"))
    }

    @Test
    fun `isValidOperationsDomain accepts https scheme with path prefix`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("https://api-v2.letu.ru/api/mindbox-regular"))
    }

    @Test
    fun `isValidOperationsDomain accepts path prefix with trailing slash`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("domain.com/api/v2/"))
    }

    @Test
    fun `isValidOperationsDomain rejects blank string`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain(""))
    }

    @Test
    fun `isValidOperationsDomain rejects invalid host with path`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("not a host/api"))
    }

    @Test
    fun `isValidOperationsDomain rejects empty path segment`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com//api"))
    }

    @Test
    fun `isValidOperationsDomain rejects query string`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/api?x=1"))
    }

    @Test
    fun `isValidOperationsDomain rejects fragment`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/api#section"))
    }

    @Test
    fun `isValidOperationsDomain rejects path segment with space`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/api v2"))
    }

    @Test
    fun `isValidOperationsDomain rejects path-only value`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("/api/v2"))
    }

    // endregion

    // region isValidOperationsDomain — percent-encoding in path prefix

    @Test
    fun `isValidOperationsDomain accepts valid percent-encoded octet`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("domain.com/a%20b"))
    }

    @Test
    fun `isValidOperationsDomain accepts percent-encoded slash within a segment`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("domain.com/a%2Fb"))
    }

    @Test
    fun `isValidOperationsDomain accepts lowercase hex in percent-encoding`() {
        assertEquals(true, SdkValidation.isValidOperationsDomain("domain.com/a%2ab"))
    }

    @Test
    fun `isValidOperationsDomain rejects non-hex percent escape`() {
        // Regression: a bare "%" not followed by two hex digits parses fine here but
        // corrupts (or fails to build) the request URL at runtime — must be rejected now.
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/a%zz"))
    }

    @Test
    fun `isValidOperationsDomain rejects dangling percent at end of path`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/a%"))
    }

    @Test
    fun `isValidOperationsDomain rejects incomplete percent escape (one hex digit)`() {
        assertEquals(false, SdkValidation.isValidOperationsDomain("domain.com/a%2"))
    }

    // endregion
}
