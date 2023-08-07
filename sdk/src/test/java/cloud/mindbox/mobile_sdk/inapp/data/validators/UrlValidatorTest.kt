package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlValidatorTest {

    private val urlValidator = UrlValidator()

    @Test
    fun `is valid http url`() {
        val url = "http://www.example.com/index.html"
        assertTrue(urlValidator.isValid(url))
    }

    @Test
    fun `is valid https url`() {
        val url = "https://www.example.com/index.html"
        assertTrue(urlValidator.isValid(url))
    }

    @Test
    fun `empty url`() {
        val url = ""
        assertFalse(urlValidator.isValid(url))
    }

    @Test
    fun `null url`() {
        assertFalse(urlValidator.isValid(null))
    }

    @Test
    fun `invalid url`() {
        val invalidUrl = "132"
        assertFalse(urlValidator.isValid(invalidUrl))
    }
}