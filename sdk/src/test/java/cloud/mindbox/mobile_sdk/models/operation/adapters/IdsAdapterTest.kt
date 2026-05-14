package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [IdsAdapter].
 *
 * Key behavior under test: GSON by default parses integer JSON values into Double
 * (e.g. 12345 → "12345.0"). The adapter explicitly uses [com.google.gson.stream.JsonReader.nextString]
 * on every value so that numeric IDs are read as raw strings without the ".0" suffix.
 */
class IdsAdapterTest {

    // Ids has @JsonAdapter(IdsAdapter::class) so plain Gson() picks it up automatically.
    private val gson = Gson()

    // region read

    @Test
    fun `read - string ID preserved as-is`() {
        val ids = gson.fromJson("""{"mindboxId":"abc123"}""", Ids::class.java)
        assertNotNull(ids)
        assertEquals("abc123", ids.ids["mindboxId"])
    }

    @Test
    fun `read - integer ID read as string without dot-zero suffix`() {
        // Critical regression test: without the adapter workaround, GSON would
        // deserialise 12345 as the Double 12345.0, producing "12345.0" in the map.
        val ids = gson.fromJson("""{"mindboxId":12345}""", Ids::class.java)
        assertNotNull(ids)
        assertEquals("12345", ids.ids["mindboxId"])
    }

    @Test
    fun `read - large integer ID preserved without scientific notation`() {
        val ids = gson.fromJson("""{"externalId":9999999999}""", Ids::class.java)
        assertNotNull(ids)
        assertEquals("9999999999", ids.ids["externalId"])
    }

    @Test
    fun `read - multiple IDs of mixed types`() {
        val ids = gson.fromJson(
            """{"mindboxId":42,"email":"user@example.com","loyaltyId":7}""",
            Ids::class.java
        )
        assertNotNull(ids)
        assertEquals("42", ids.ids["mindboxId"])
        assertEquals("user@example.com", ids.ids["email"])
        assertEquals("7", ids.ids["loyaltyId"])
    }

    @Test
    fun `read - empty object produces empty map`() {
        val ids = gson.fromJson("""{}""", Ids::class.java)
        assertNotNull(ids)
        assertTrue(ids.ids.isEmpty())
    }

    @Test
    fun `read - JSON null returns null Ids`() {
        val ids = gson.fromJson("null", Ids::class.java)
        assertNull(ids)
    }

    // endregion

    // region write

    @Test
    fun `write - string ID serialized as JSON string`() {
        val ids = Ids("mindboxId" to "abc123")
        val json = gson.toJson(ids)
        assertTrue(json.contains(""""mindboxId":"abc123""""))
    }

    @Test
    fun `write - null Ids serialized as JSON null`() {
        val json = gson.toJson(null as Ids?)
        assertEquals("null", json)
    }

    @Test
    fun `write - multiple IDs serialized correctly`() {
        val ids = Ids("mindboxId" to "42", "email" to "user@example.com")
        val json = gson.toJson(ids)
        assertTrue(json.contains(""""mindboxId":"42""""))
        assertTrue(json.contains(""""email":"user@example.com""""))
    }

    @Test
    fun `write - null value in map is dropped by Gson`() {
        // GSON's nullValue() with serializeNulls=false silently drops name+null map pairs.
        // An Ids entry with a null value is absent from the serialized output.
        val ids = Ids(mapOf("mindboxId" to null))
        val json = gson.toJson(ids)
        assertEquals("{}", json)
    }

    // endregion

    // region round-trip

    @Test
    fun `round-trip - integer ID survives serialize then deserialize`() {
        // Ids stores everything as String, so we start with a string "42".
        val original = Ids("mindboxId" to "42")
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, Ids::class.java)
        assertNotNull(restored)
        assertEquals("42", restored.ids["mindboxId"])
    }

    @Test
    fun `round-trip - multiple string IDs preserved`() {
        // null values are excluded: IdsAdapter.read() hangs on null map values (see bug comment above).
        val original = Ids("a" to "1", "b" to "hello")
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, Ids::class.java)
        assertNotNull(restored)
        assertEquals(original.ids, restored.ids)
    }

    // endregion
}
