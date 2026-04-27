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

    // endregion
}
