package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

internal class InAppDelayTimeDeserializerTest {

    private val deserializer = InAppDelayTimeDeserializer()

    @Test
    fun `when json is a valid string then returns the string`() {
        val jsonString = "00:30:00"
        val json = JsonPrimitive(jsonString)
        val result = deserializer.deserialize(json, null, null)
        assertEquals(jsonString, result)
    }

    @Test
    fun `when json is an empty string then returns the empty string`() {
        val json = JsonPrimitive("")
        val result = deserializer.deserialize(json, null, null)
        assertEquals("", result)
    }

    @Test
    fun `when json is a number then returns null`() {
        val json = JsonPrimitive(123)
        val result = deserializer.deserialize(json, null, null)
        assertNull(result)
    }

    @Test
    fun `when json is a boolean then returns null`() {
        val json = JsonPrimitive(true)
        val result = deserializer.deserialize(json, null, null)
        assertNull(result)
    }

    @Test
    fun `when json is a JsonObject then returns null`() {
        val json = JsonObject()
        val result = deserializer.deserialize(json, null, null)
        assertNull(result)
    }

    @Test
    fun `when json is a JsonArray then returns null`() {
        val json = JsonArray()
        val result = deserializer.deserialize(json, null, null)
        assertNull(result)
    }

    @Test
    fun `when json is JsonNull then returns null`() {
        val json = JsonNull.INSTANCE
        val result = deserializer.deserialize(json, null, null)
        assertNull(result)
    }
}
