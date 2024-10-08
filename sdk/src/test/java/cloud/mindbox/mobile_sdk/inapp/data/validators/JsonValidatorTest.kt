package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonValidatorTest {

    private val jsonValidator = JsonValidator()

    @Test
    fun `is valid json string`() {
        val validJson = "{\"inappid\":\"someInAppId\"}"
        assertTrue(jsonValidator.isValid(validJson))
    }

    @Test
    fun testInvalidJson() {
        val invalidJson = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\""
        assertFalse(jsonValidator.isValid(invalidJson))
    }

    @Test
    fun `is not valid json string`() {
        val inValidJson = "12sd3"
        assertFalse(jsonValidator.isValid(inValidJson))
    }

    @Test
    fun `is empty string`() {
        val emptyString = ""
        assertFalse(jsonValidator.isValid(emptyString))
    }

    @Test
    fun `is valid JSON string with line breaks`() {
        val validJson = "{\n\"inappid\":\"someInAppId\"\n}"
        assertTrue(jsonValidator.isValid(validJson))
    }

    @Test
    fun `is null string`() {
        assertFalse(jsonValidator.isValid(null))
    }

    @Test
    fun `is valid JSON array string`() {
        val jsonString = """
        [
          {"name": "John", "age": 30},
          {"name": "Jane", "age": 25}
        ]
        """.trimIndent()
        assertTrue(jsonValidator.isValid(jsonString))
    }

    @Test
    fun `is valid JSON with spaces`() {
        val validJson = " { \"inappid\": \"someInAppId\" } "
        assertTrue(jsonValidator.isValid(validJson))
    }

    @Test
    fun `is valid empty JSON array `() {
        val validJson = "[]"
        assertTrue(jsonValidator.isValid(validJson))
    }
}
