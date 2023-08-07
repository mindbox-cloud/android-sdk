package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.json.JSONException
import org.json.JSONObject
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
    fun `is null string`() {
        assertFalse(jsonValidator.isValid(null))
    }
}