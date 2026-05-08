package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerFieldsAdapterTest {

    // CustomFields has @JsonAdapter(CustomerFieldsAdapter::class) so plain Gson() picks it up.
    private val gson = Gson()

    // region read

    @Test
    fun `read - string field deserialized correctly`() {
        val fields = gson.fromJson("""{"name":"John"}""", CustomFields::class.java)
        assertNotNull(fields)
        assertEquals("John", fields.fields?.get("name"))
    }

    @Test
    fun `read - numeric field deserialized as Double (GSON default for numbers)`() {
        val fields = gson.fromJson("""{"age":30}""", CustomFields::class.java)
        assertNotNull(fields)
        // GSON deserializes JSON numbers into Map<String, Any?> as Double
        assertEquals(30.0, fields.fields?.get("age"))
    }

    @Test
    fun `read - boolean field deserialized correctly`() {
        val fields = gson.fromJson("""{"active":true}""", CustomFields::class.java)
        assertNotNull(fields)
        assertEquals(true, fields.fields?.get("active"))
    }

    @Test
    fun `read - null field value deserialized as null`() {
        val fields = gson.fromJson("""{"optional":null}""", CustomFields::class.java)
        assertNotNull(fields)
        assertNull(fields.fields?.get("optional"))
    }

    @Test
    fun `read - multiple fields deserialized correctly`() {
        val fields = gson.fromJson(
            """{"name":"Alice","score":99.5,"active":false}""",
            CustomFields::class.java
        )
        assertNotNull(fields)
        assertEquals("Alice", fields.fields?.get("name"))
        assertEquals(99.5, fields.fields?.get("score"))
        assertEquals(false, fields.fields?.get("active"))
    }

    @Test
    fun `read - empty object produces empty map`() {
        val fields = gson.fromJson("""{}""", CustomFields::class.java)
        assertNotNull(fields)
        assertTrue(fields.fields?.isEmpty() ?: false)
    }

    @Test
    fun `read - JSON null returns null CustomFields`() {
        val fields = gson.fromJson("null", CustomFields::class.java)
        assertNull(fields)
    }

    // endregion

    // region write

    @Test
    fun `write - string field serialized as JSON string`() {
        val fields = CustomFields("city" to "Moscow")
        val json = gson.toJson(fields)
        assertTrue(json.contains(""""city":"Moscow""""))
    }

    @Test
    fun `write - null CustomFields serialized as JSON null`() {
        val json = gson.toJson(null as CustomFields?)
        assertEquals("null", json)
    }

    @Test
    fun `write - CustomFields with null fields map serialized as JSON null`() {
        val fields = CustomFields(fields = null)
        val json = gson.toJson(fields)
        assertEquals("null", json)
    }

    @Test
    fun `write - multiple fields serialized correctly`() {
        val fields = CustomFields("a" to "1", "b" to 2)
        val json = gson.toJson(fields)
        assertTrue(json.contains(""""a":"1""""))
        assertTrue(json.contains(""""b":2"""))
    }

    @Test
    fun `write - null value in fields is dropped by Gson`() {
        // CustomerFieldsAdapter.write() calls gson.toJson(value.fields).
        // GSON's nullValue() with serializeNulls=false silently drops name+null pairs
        // even inside maps, so null field values are absent from the output.
        val fields = CustomFields(mapOf("key" to null as Any?))
        val json = gson.toJson(fields)
        assertEquals("{}", json)
    }

    // endregion

    // region convertTo

    @Test
    fun `convertTo - maps fields to typed data class`() {
        data class Profile(val name: String?, val age: Double?)

        val fields = CustomFields("name" to "Bob", "age" to 25.0)
        val profile = fields.convertTo(Profile::class.java)

        assertNotNull(profile)
        assertEquals("Bob", profile!!.name)
        assertEquals(25.0, profile.age)
    }

    @Test
    fun `convertTo - returns null for null fields map`() {
        val fields = CustomFields(fields = null)
        val result = fields.convertTo(Map::class.java)
        assertNull(result)
    }

    // endregion

    // region round-trip

    @Test
    fun `round-trip - string fields preserved`() {
        val original = CustomFields("x" to "hello", "y" to "world")
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, CustomFields::class.java)
        assertNotNull(restored)
        assertEquals("hello", restored.fields?.get("x"))
        assertEquals("world", restored.fields?.get("y"))
    }

    // endregion
}
