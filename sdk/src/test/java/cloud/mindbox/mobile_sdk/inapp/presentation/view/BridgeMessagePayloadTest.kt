package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import com.google.gson.JsonParser
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BridgeMessagePayloadTest {

    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private fun request(payloadJson: String): BridgeMessage.Request =
        gson.fromBridgeMessage(
            """{"type":"request","action":"showInApp","payload":$payloadJson,"id":"id-1","version":1,"timestamp":1}"""
        ) as BridgeMessage.Request

    @Test
    fun `string payload passes through unchanged`() {
        val message = request(""""{\"inappId\":\"story-1\"}"""")

        assertEquals("""{"inappId":"story-1"}""", message.payload)
    }

    @Test
    fun `object payload is kept as its json text`() {
        val message = request("""{"inappId":"story-1","params":{"a":1}}""")

        assertEquals(
            JsonParser.parseString("""{"inappId":"story-1","params":{"a":1}}"""),
            JsonParser.parseString(message.payload!!)
        )
    }

    @Test
    fun `array payload is kept as its json text`() {
        val message = request("""[1,"two"]""")

        assertEquals("""[1,"two"]""", message.payload)
    }

    @Test
    fun `null payload stays null`() {
        val message = request("null")

        assertNull(message.payload)
    }

    @Test
    fun `unreadable envelope parses to null instead of throwing`() {
        assertNull(gson.fromBridgeMessage("not a json {"))
    }

    @Test
    fun `envelope that is valid json but not an object parses to null instead of throwing`() {
        assertNull(gson.fromBridgeMessage("42"))
        assertNull(gson.fromBridgeMessage("[1,2]"))
        assertNull(gson.fromBridgeMessage(""""text""""))
        assertNull(gson.fromBridgeMessage("true"))
    }

    @Test
    fun `sync operation error payload passes through as structural json`() {
        val payloadJson = """{"status":"ValidationError","validationMessages":[{"message":"bad"}]}"""

        assertEquals(payloadJson, gson.toBridgeErrorPayload(WebViewSyncOperationException(payloadJson)))
    }

    @Test
    fun `plain error is wrapped into the error envelope`() {
        assertEquals(
            """{"error":"Nobody is looking at this page"}""",
            gson.toBridgeErrorPayload(IllegalStateException("Nobody is looking at this page"))
        )
    }

    @Test
    fun `error without a message falls back to the unknown error payload`() {
        assertEquals(
            BridgeMessage.UNKNOWN_ERROR_PAYLOAD,
            gson.toBridgeErrorPayload(IllegalStateException())
        )
    }

    @Test
    fun `outgoing payload is serialized as a string`() {
        val response = BridgeMessage.createResponseAction(
            request(""""{}""""),
            """{"success":true}"""
        )

        val json = JsonParser.parseString(gson.toJson(response)).asJsonObject
        assertEquals("""{"success":true}""", json.get("payload").asString)
    }
}
