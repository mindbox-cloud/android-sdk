package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class JsonElementExtensionsTest {

    @Test
    fun `getAsIntOrNull returns null for missing key`() {
        val json = JsonObject()
        assertNull(json.getAsIntOrNull("nonExistentKey"))
    }

    @Test
    fun `getAsIntOrNull returns null for non-primitive value`() {
        val json = JsonObject().apply {
            add("key", JsonObject())
        }
        assertNull(json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns null for non-number string`() {
        val json = JsonObject().apply {
            addProperty("key", "not a number")
        }
        assertNull(json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns null for number exceeding Int MAX_VALUE`() {
        val json = JsonObject().apply {
            addProperty("key", Int.MAX_VALUE.toLong() + 1)
        }
        assertNull(json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns number for valid number`() {
        val json = JsonObject().apply {
            addProperty("key", 42)
        }
        assertEquals(42, json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns number for valid number string`() {
        val json = JsonObject().apply {
            addProperty("key", "42")
        }
        assertEquals(42, json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns null for number less than Int MIN_VALUE`() {
        val json = JsonObject().apply {
            addProperty("key", Int.MIN_VALUE.toLong() - 1)
        }
        assertNull(json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns number for Int MIN_VALUE`() {
        val json = JsonObject().apply {
            addProperty("key", Int.MIN_VALUE)
        }
        assertEquals(Int.MIN_VALUE, json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns number for string with Int MIN_VALUE`() {
        val json = JsonObject().apply {
            addProperty("key", "-2147483648")
        }
        assertEquals(Int.MIN_VALUE, json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsIntOrNull returns null for string less than Int MIN_VALUE`() {
        val json = JsonObject().apply {
            addProperty("key", "-2147483649")
        }
        assertNull(json.getAsIntOrNull("key"))
    }

    @Test
    fun `getAsTimeSpan returns null for missing key`() {
        val json = JsonObject()
        assertNull(json.getAsTimeSpan("nonExistentKey"))
    }

    @Test
    fun `getAsTimeSpan returns null for non-primitive value`() {
        val json = JsonObject().apply {
            add("key", JsonObject())
        }
        assertNull(json.getAsTimeSpan("key"))
    }

    @Test
    fun `getAsTimeSpan returns null for non-string value`() {
        val json = JsonObject().apply {
            addProperty("key", 42)
        }
        assertNull(json.getAsTimeSpan("key"))
    }

    @Test
    fun `getAsTimeSpan returns string for valid string value`() {
        val json = JsonObject().apply {
            addProperty("key", "0.00:00:10")
        }
        assertEquals("0.00:00:10", json.getAsTimeSpan("key"))
    }
}
