package cloud.mindbox.mobile_sdk.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MindboxErrorAdapter] via the public [MindboxError.toJson] API.
 *
 * The adapter handles two directions:
 *  - write (toJson) — used by SDK clients to log/pass errors around — fully implemented.
 *  - read (fromJson) — parsing is effectively non-functional in the current implementation
 *    (the read() method reads the JSON key name instead of key value, causing it to always
 *    fall through to `else -> null`). Tests below document this known behavior.
 */
class MindboxErrorAdapterTest {

    // MindboxError subtypes have @JsonAdapter(MindboxErrorAdapter::class)
    private val gson = Gson()

    // region write / toJson — Validation

    @Test
    fun `toJson Validation - contains type MindboxError`() {
        val error = MindboxError.Validation(
            statusCode = 200,
            status = "ValidationError",
            validationMessages = emptyList(),
        )
        val json = error.toJson()
        assertTrue(json.contains(""""type":"MindboxError""""))
    }

    @Test
    fun `toJson Validation - contains statusCode`() {
        val error = MindboxError.Validation(200, "ValidationError", emptyList())
        val json = error.toJson()
        assertTrue(json.contains(""""statusCode":200"""))
    }

    @Test
    fun `toJson Validation - contains status`() {
        val error = MindboxError.Validation(200, "ValidationError", emptyList())
        val json = error.toJson()
        assertTrue(json.contains(""""status":"ValidationError""""))
    }

    @Test
    fun `toJson Validation - contains empty validationMessages array`() {
        val error = MindboxError.Validation(200, "ValidationError", emptyList())
        val json = error.toJson()
        assertTrue(json.contains(""""validationMessages":[]"""))
    }

    @Test
    fun `toJson Validation - contains validationMessages with entries`() {
        val error = MindboxError.Validation(
            statusCode = 200,
            status = "ValidationError",
            validationMessages = listOf(
                ValidationMessage(message = "field required", location = "email"),
            ),
        )
        val json = error.toJson()
        assertTrue(json.contains(""""message":"field required""""))
        assertTrue(json.contains(""""location":"email""""))
    }

    @Test
    fun `toJson Validation - full JSON structure`() {
        val error = MindboxError.Validation(200, "Ok", emptyList())
        val json = error.toJson()
        assertEquals(
            """{"type":"MindboxError","data":{"statusCode":200,"status":"Ok","validationMessages":[]}}""",
            json,
        )
    }

    // endregion

    // region write / toJson — Protocol

    @Test
    fun `toJson Protocol - contains type MindboxError`() {
        val error = MindboxError.Protocol(
            statusCode = 400,
            status = "Error",
            errorMessage = "Bad request",
            errorId = "err-1",
            httpStatusCode = 400,
        )
        val json = error.toJson()
        assertTrue(json.contains(""""type":"MindboxError""""))
    }

    @Test
    fun `toJson Protocol - full JSON structure`() {
        val error = MindboxError.Protocol(400, "Error", "Bad request", "err-1", 400)
        val json = error.toJson()
        assertEquals(
            """{"type":"MindboxError","data":{"statusCode":400,"status":"Error","errorMessage":"Bad request","errorId":"err-1","httpStatusCode":400}}""",
            json,
        )
    }

    @Test
    fun `toJson Protocol - null optional fields omitted from JSON`() {
        // GSON's nullValue() silently skips a name+value pair when serializeNulls = false
        // (the default). MindboxErrorAdapter does not override this, so null fields are
        // absent from the output — not present as "null". This is the current behavior.
        val error = MindboxError.Protocol(403, "Forbidden", null, null, null)
        val json = error.toJson()
        assertFalse("null errorMessage should be omitted", json.contains("errorMessage"))
        assertFalse("null errorId should be omitted", json.contains("errorId"))
        assertFalse("null httpStatusCode should be omitted", json.contains("httpStatusCode"))
    }

    // endregion

    // region write / toJson — InternalServer

    @Test
    fun `toJson InternalServer - contains type MindboxError`() {
        val error = MindboxError.InternalServer(500, "ServerError", "Internal error", "id-1", 500)
        val json = error.toJson()
        assertTrue(json.contains(""""type":"MindboxError""""))
    }

    @Test
    fun `toJson InternalServer - full JSON structure`() {
        val error = MindboxError.InternalServer(500, "ServerError", "Internal error", "id-1", 500)
        val json = error.toJson()
        assertEquals(
            """{"type":"MindboxError","data":{"statusCode":500,"status":"ServerError","errorMessage":"Internal error","errorId":"id-1","httpStatusCode":500}}""",
            json,
        )
    }

    // endregion

    // region write / toJson — UnknownServer

    @Test
    fun `toJson UnknownServer - contains type NetworkError`() {
        val error = MindboxError.UnknownServer()
        val json = error.toJson()
        assertTrue(json.contains(""""type":"NetworkError""""))
    }

    @Test
    fun `toJson UnknownServer - default constructor full JSON`() {
        val error = MindboxError.UnknownServer()
        val json = error.toJson()
        // Default constructor sets errorMessage = "Cannot reach server", all else null
        assertTrue(json.contains(""""errorMessage":"Cannot reach server""""))
    }

    @Test
    fun `toJson UnknownServer - with all fields`() {
        val error = MindboxError.UnknownServer(503, "Unavailable", "Service down", "id-2", 503)
        val json = error.toJson()
        assertEquals(
            """{"type":"NetworkError","data":{"statusCode":503,"status":"Unavailable","errorMessage":"Service down","errorId":"id-2","httpStatusCode":503}}""",
            json,
        )
    }

    // endregion

    // region write / toJson — Unknown

    @Test
    fun `toJson Unknown - contains type InternalError`() {
        val error = MindboxError.Unknown()
        val json = error.toJson()
        assertTrue(json.contains(""""type":"InternalError""""))
    }

    @Test
    fun `toJson Unknown - null throwable produces empty data object`() {
        // Both errorName and errorMessage are null → both name+null pairs are silently
        // dropped by GSON (serializeNulls = false). The data object is empty.
        val error = MindboxError.Unknown(throwable = null)
        val json = error.toJson()
        assertFalse("null errorName should be omitted", json.contains("errorName"))
        assertFalse("null errorMessage should be omitted", json.contains("errorMessage"))
        assertTrue("data object should be present but empty", json.contains(""""data":{}"""))
    }

    @Test
    fun `toJson Unknown - throwable class name and message included`() {
        val throwable = RuntimeException("something went wrong")
        val error = MindboxError.Unknown(throwable)
        val json = error.toJson()
        assertTrue(json.contains("RuntimeException"))
        assertTrue(json.contains("something went wrong"))
    }

    // endregion

    // region read / fromJson — current behavior documentation

    @Test
    fun `fromJson Validation - current behavior returns null (read is not implemented)`() {
        // MindboxErrorAdapter.read() calls nextString() after beginObject() which reads
        // the key name "type" instead of its value. None of the when-branches match "type",
        // so the method always returns null. This test documents that known limitation so
        // that a future fix or migration will be noticed immediately.
        val json = MindboxError.Validation(200, "Ok", emptyList()).toJson()
        val result = gson.fromJson(json, MindboxError.Validation::class.java)
        assertNull(result)
    }

    @Test
    fun `fromJson Protocol - current behavior returns null`() {
        val json = MindboxError.Protocol(400, "Error", null, null, null).toJson()
        val result = gson.fromJson(json, MindboxError.Protocol::class.java)
        assertNull(result)
    }

    @Test
    fun `fromJson UnknownServer - current behavior returns null`() {
        val json = MindboxError.UnknownServer().toJson()
        val result = gson.fromJson(json, MindboxError.UnknownServer::class.java)
        assertNull(result)
    }

    // endregion

    // region toJson output is valid JSON

    @Test
    fun `toJson output is parseable by Gson as JsonObject`() {
        listOf(
            MindboxError.Validation(200, "Ok", emptyList()),
            MindboxError.Protocol(400, "Error", null, null, null),
            MindboxError.InternalServer(500, "Err", null, null, null),
            MindboxError.UnknownServer(),
            MindboxError.Unknown(),
        ).forEach { error ->
            val json = error.toJson()
            val parsed = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            assertNotNull("toJson() should produce valid JSON for ${error::class.simpleName}", parsed)
            assertTrue(
                "${error::class.simpleName} JSON should have 'type' key",
                parsed.has("type"),
            )
            assertTrue(
                "${error::class.simpleName} JSON should have 'data' key",
                parsed.has("data"),
            )
        }
    }

    // endregion
}
