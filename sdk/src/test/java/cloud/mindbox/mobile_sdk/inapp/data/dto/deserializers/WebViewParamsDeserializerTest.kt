package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class WebViewParamsDeserializerTest {

    private lateinit var gson: Gson

    @Before
    fun setUp() {
        gson = Gson()
    }

    @Test
    fun `deserialize converts all values to string`() {
        val nestedObject = JsonObject().apply { addProperty("nested", "value") }
        val json = JsonObject().apply {
            addProperty("formId", "73379")
            addProperty("validKey", "validValue")
            addProperty("numberKey", 123)
            add("objectKey", nestedObject)
            add("nullKey", JsonNull.INSTANCE)
        }
        val webViewLayerJson = createWebViewLayerJson(
            baseUrl = "https://inapp.local",
            contentUrl = "https://api.example.com",
            params = json
        )
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertEquals("73379", result.params!!["formId"])
        assertEquals("validValue", result.params["validKey"])
        assertEquals("123", result.params["numberKey"])
        assertEquals("{\"nested\":\"value\"}", result.params["objectKey"])
        assertFalse(result.params.containsKey("nullKey"))
    }

    @Test
    fun `deserialize returns null when params is null`() {
        val webViewLayerJson = JsonObject().apply {
            addProperty("baseUrl", "https://inapp.local")
            addProperty("contentUrl", "https://api.example.com")
            addProperty("\$type", "webview")
            add("params", JsonNull.INSTANCE)
        }
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertNull(result.params)
    }

    @Test
    fun `deserialize returns empty map when params is not object`() {
        val webViewLayerJson = JsonObject().apply {
            addProperty("baseUrl", "https://inapp.local")
            addProperty("contentUrl", "https://api.example.com")
            addProperty("\$type", "webview")
            add("params", JsonArray().apply { add("notAnObject") })
        }
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertTrue(result.params?.isEmpty() == true)
    }

    @Test
    fun `deserialize converts number and boolean primitive to string`() {
        val json = JsonObject().apply {
            addProperty("stringVal", "ok")
            addProperty("intVal", 42)
            addProperty("doubleVal", 3.14)
            addProperty("boolVal", true)
        }
        val webViewLayerJson = createWebViewLayerJson(
            baseUrl = "https://inapp.local",
            contentUrl = "https://api.example.com",
            params = json
        )
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertEquals("ok", result.params!!["stringVal"])
        assertEquals("42", result.params["intVal"])
        assertEquals("3.14", result.params["doubleVal"])
        assertEquals("true", result.params["boolVal"])
    }

    @Test
    fun `deserialize empty object returns empty map`() {
        val webViewLayerJson = createWebViewLayerJson(
            baseUrl = "https://inapp.local",
            contentUrl = "https://api.example.com",
            params = JsonObject()
        )
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertTrue(result.params?.isEmpty() == true)
    }

    @Test
    fun `deserialize preserves all string values`() {
        val json = JsonObject().apply {
            addProperty("key1", "value1")
            addProperty("key2", "")
            addProperty("key3", "value3")
        }
        val webViewLayerJson = createWebViewLayerJson(
            baseUrl = "https://inapp.local",
            contentUrl = "https://api.example.com",
            params = json
        )
        val result = gson.fromJson(webViewLayerJson, BackgroundDto.LayerDto.WebViewLayerDto::class.java)
        assertEquals(
            mapOf("key1" to "value1", "key2" to "", "key3" to "value3"),
            result.params
        )
    }

    private fun createWebViewLayerJson(
        baseUrl: String,
        contentUrl: String,
        params: JsonObject
    ): JsonObject = JsonObject().apply {
        addProperty("baseUrl", baseUrl)
        addProperty("contentUrl", contentUrl)
        addProperty("\$type", "webview")
        add("params", params)
    }
}
