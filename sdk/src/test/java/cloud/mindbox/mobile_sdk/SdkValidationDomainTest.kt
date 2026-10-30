package cloud.mindbox.mobile_sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // region deriveTrackingHost

    @Test
    fun `deriveTrackingHost yields the four names the infrastructure serves`() {
        assertEquals("api-tracking.mindbox.ru", SdkValidation.deriveTrackingHost("api.mindbox.ru"))
        assertEquals("api-tracking.b.mindbox.ru", SdkValidation.deriveTrackingHost("api.b.mindbox.ru"))
        assertEquals("api-tracking.s.mindbox.ru", SdkValidation.deriveTrackingHost("api.s.mindbox.ru"))
        assertEquals("api-tracking-staging.mindbox.ru", SdkValidation.deriveTrackingHost("api-staging.mindbox.ru"))
    }

    @Test
    fun `deriveTrackingHost ignores scheme, trailing slash and case`() {
        assertEquals("api-tracking.mindbox.ru", SdkValidation.deriveTrackingHost("https://api.mindbox.ru/"))
        assertEquals("api-tracking.mindbox.ru", SdkValidation.deriveTrackingHost("http://API.mindbox.ru"))
        assertEquals("api-tracking-Staging.mindbox.ru", SdkValidation.deriveTrackingHost("Api-Staging.mindbox.ru"))
    }

    @Test
    fun `deriveTrackingHost is null when the first label is not api`() {
        assertNull(SdkValidation.deriveTrackingHost("gost-api.mindbox.ru"))
        assertNull(SdkValidation.deriveTrackingHost("apix.mindbox.ru"))
        assertNull(SdkValidation.deriveTrackingHost("mobile-sdk-test.mindbox.ru"))
        assertNull(SdkValidation.deriveTrackingHost("proxy.client.ru"))
    }

    @Test
    fun `deriveTrackingHost is null for an ip address, a single label and a blank value`() {
        assertNull(SdkValidation.deriveTrackingHost("192.168.0.1"))
        assertNull(SdkValidation.deriveTrackingHost("api"))
        assertNull(SdkValidation.deriveTrackingHost(""))
        assertNull(SdkValidation.deriveTrackingHost("   "))
    }

    @Test
    fun `deriveTrackingHost refuses a label longer than DNS allows`() {
        val suffix = "x".repeat(60)

        assertNull(SdkValidation.deriveTrackingHost("api-$suffix.mindbox.ru"))
    }

    @Test
    fun `resolveTrackingHost prefers the explicit trackingDomain`() {
        assertEquals("tracking.client.ru", SdkValidation.resolveTrackingHost("https://tracking.client.ru/", "api.mindbox.ru"))
        assertEquals("api-tracking.mindbox.ru", SdkValidation.resolveTrackingHost(null, "api.mindbox.ru"))
        assertEquals("api-tracking.mindbox.ru", SdkValidation.resolveTrackingHost("  ", "api.mindbox.ru"))
        assertNull(SdkValidation.resolveTrackingHost(null, "gost-api.mindbox.ru"))
    }

    @Test
    fun `toTrackingBaseUrl always uses https`() {
        assertEquals("https://api-tracking.mindbox.ru", SdkValidation.toTrackingBaseUrl("api-tracking.mindbox.ru"))
        assertEquals("https://api-tracking.mindbox.ru", SdkValidation.toTrackingBaseUrl("http://api-tracking.mindbox.ru/"))
    }

    @Test
    fun `toTrackingBaseUrl keeps the path prefix`() {
        assertEquals("https://proxy.client.ru/mindbox", SdkValidation.toTrackingBaseUrl("http://proxy.client.ru/mindbox/"))
    }

    // endregion

    // region trackingDomain validation

    @Test
    fun `isValidTrackingDomain follows the operationsDomain rule`() {
        assertTrue(SdkValidation.isValidTrackingDomain("api-tracking.mindbox.ru"))
        assertTrue(SdkValidation.isValidTrackingDomain("https://api-tracking.mindbox.ru/"))
        assertTrue(SdkValidation.isValidTrackingDomain("proxy.client.ru/mindbox/tracking"))
        assertFalse(SdkValidation.isValidTrackingDomain("api-tracking.mindbox.ru:443"))
        assertFalse(SdkValidation.isValidTrackingDomain("api-tracking.mindbox.ru//api"))
        assertFalse(SdkValidation.isValidTrackingDomain("not a host"))
        assertFalse(SdkValidation.isValidTrackingDomain(""))
    }

    @Test
    fun `validateConfiguration reports an invalid trackingDomain as a non-critical error`() {
        val errors = SdkValidation.validateConfiguration(
            domain = "api.mindbox.ru",
            endpointId = "endpoint",
            previousDeviceUUID = "",
            previousInstallationId = "",
            trackingDomain = "api-tracking.mindbox.ru:443",
        )

        assertEquals(listOf(SdkValidation.Error.INVALID_TRACKING_DOMAIN), errors)
        assertFalse(SdkValidation.Error.INVALID_TRACKING_DOMAIN.critical)
    }

    @Test
    fun `validateConfiguration accepts a valid or absent trackingDomain`() {
        assertTrue(
            SdkValidation.validateConfiguration("api.mindbox.ru", "endpoint", "", "", trackingDomain = "tracking.client.ru").isEmpty()
        )
        assertTrue(SdkValidation.validateConfiguration("api.mindbox.ru", "endpoint", "", "", trackingDomain = null).isEmpty())
    }

    // endregion
}
