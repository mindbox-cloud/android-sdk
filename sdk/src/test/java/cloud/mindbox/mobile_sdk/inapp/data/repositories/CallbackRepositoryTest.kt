package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.validators.JsonValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.UrlValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.XmlValidator
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CallbackRepositoryTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var callbackRepository: CallbackRepositoryImpl

    @MockK
    private lateinit var jsonValidator: JsonValidator

    @MockK
    private lateinit var xmlValidator: XmlValidator

    @MockK
    private lateinit var urlValidator: UrlValidator


    @Test
    fun testValidateUserString_InvalidXml() {
        val userString = "<xml>data</xml>"
        every { xmlValidator.isValid(userString) } returns true
        every { jsonValidator.isValid(userString) } returns false
        every { urlValidator.isValid(userString) } returns false

        val result = callbackRepository.validateUserString(userString)
        assertFalse(result)
    }

    @Test
    fun testValidateUserString_InvalidJson() {
        val userString = "{\"name\":\"John\",\"age\":30}"
        every { xmlValidator.isValid(userString) } returns false
        every { jsonValidator.isValid(userString) } returns true
        every { urlValidator.isValid(userString) } returns false

        val result = callbackRepository.validateUserString(userString)
        assertFalse(result)
    }

    @Test
    fun testValidateUserString_InvalidUrl() {
        val userString = "https://www.example.com"
        every { xmlValidator.isValid(userString) } returns false
        every { jsonValidator.isValid(userString) } returns false
        every { urlValidator.isValid(userString) } returns true

        val result = callbackRepository.validateUserString(userString)
        assertFalse(result)
    }

    @Test
    fun testValidateUserString_Valid() {
        val userString = "Some valid user string"
        every { xmlValidator.isValid(userString) } returns false
        every { jsonValidator.isValid(userString) } returns false
        every { urlValidator.isValid(userString) } returns false

        val result = callbackRepository.validateUserString(userString)
        assertTrue(result)
    }

    @Test
    fun testIsValidUrl_ValidUrl() {
        val url = "https://www.example.com"
        every { urlValidator.isValid(url) } returns true

        val result = callbackRepository.isValidUrl(url)
        assertTrue(result)
    }

    @Test
    fun testIsValidUrl_InvalidUrl() {
        val url = "invalid-url"
        every { urlValidator.isValid(url) } returns false

        val result = callbackRepository.isValidUrl(url)
        assertFalse(result)
    }

    @Test
    fun `test empty string`() {
        every {
            urlValidator.isValid(any())
        } returns false
        every {
            xmlValidator.isValid(any())
        } returns false
        every {
            jsonValidator.isValid(any())
        } returns false
        assertFalse(callbackRepository.validateUserString(""))
    }
}