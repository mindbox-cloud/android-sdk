package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

internal class InAppIsPriorityDeserializerTest {
    private val deserializer = InAppIsPriorityDeserializer()

    @Test
    fun `when json is null returns false`() {
        val result = deserializer.deserialize(null, null, null)
        assertFalse(result)
    }

    @Test
    fun `when json is true returns true`() {
        val json = JsonPrimitive(true)
        val result = deserializer.deserialize(json, null, null)
        assertEquals(true, result)
    }

    @Test
    fun `when json is false returns false`() {
        val json = JsonPrimitive(false)
        val result = deserializer.deserialize(json, null, null)
        assertEquals(false, result)
    }

    @Test
    fun `when json is string true returns false`() {
        val json = JsonPrimitive("true")
        val result = deserializer.deserialize(json, null, null)
        assertEquals(false, result)
    }

    @Test
    fun `when json is number returns false`() {
        val json = JsonPrimitive(1)
        val result = deserializer.deserialize(json, null, null)
        assertEquals(false, result)
    }

    @Test
    fun `when json is object returns false`() {
        val json = JsonObject()
        val result = deserializer.deserialize(json, null, null)
        assertEquals(false, result)
    }

    @Test
    fun `when json is array returns false`() {
        val json = JsonArray()
        val result = deserializer.deserialize(json, null, null)
        assertEquals(false, result)
    }
}
