package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.fromJsonTyped
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InAppTagsDeserializerTest {

    private lateinit var gson: Gson

    @Before
    fun setUp() {
        val mapType = object : TypeToken<Map<String, String>?>() {}.type
        gson = GsonBuilder()
            .registerTypeAdapter(mapType, InAppTagsDeserializer())
            .create()
    }

    private fun deserialize(json: String): Map<String, String>? =
        gson.fromJsonTyped<Map<String, String>?>(json)

    @Test
    fun `deserialize returns string values as is`() {
        val inputJson = """{"layer": "webView", "type": "onboarding"}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView", "type" to "onboarding"), actualResult)
    }

    @Test
    fun `deserialize skips number values`() {
        val inputJson = """{"layer": "webView", "count": 42}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView"), actualResult)
    }

    @Test
    fun `deserialize skips boolean values`() {
        val inputJson = """{"layer": "webView", "isActive": true}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView"), actualResult)
    }

    @Test
    fun `deserialize skips object values`() {
        val inputJson = """{"layer": "webView", "nested": {"key": "value"}}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView"), actualResult)
    }

    @Test
    fun `deserialize skips null values`() {
        val inputJson = """{"layer": "webView", "nullKey": null}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView"), actualResult)
    }

    @Test
    fun `deserialize returns null when json is null`() {
        val actualResult = deserialize("null")
        assertNull(actualResult)
    }

    @Test
    fun `deserialize returns null when json is not an object`() {
        val actualResult = deserialize("""["item1", "item2"]""")
        assertNull(actualResult)
    }

    @Test
    fun `deserialize returns empty map when all values are non-string`() {
        val inputJson = """{"count": 42, "flag": true, "nested": {}}"""
        val actualResult = deserialize(inputJson)
        assertTrue(actualResult?.isEmpty() == true)
    }

    @Test
    fun `deserialize returns empty map for empty object`() {
        val actualResult = deserialize("{}")
        assertTrue(actualResult?.isEmpty() == true)
    }

    @Test
    fun `deserialize preserves empty string values`() {
        val inputJson = """{"layer": "", "type": "onboarding"}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "", "type" to "onboarding"), actualResult)
    }

    @Test
    fun `deserialize skips array values`() {
        val inputJson = """{"layer": "webView", "items": [1, 2, 3]}"""
        val actualResult = deserialize(inputJson)
        assertEquals(mapOf("layer" to "webView"), actualResult)
    }
}
