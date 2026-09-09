package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import com.google.gson.JsonParser
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BridgeMessagePayloadTest {

    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private fun request(payloadJson: String): BridgeMessage.Request =
        gson.fromBridgeMessage(
            """{"type":"request","action":"showInApp","payload":$payloadJson,"id":"id-1","version":1,"timestamp":1}"""
        ) as BridgeMessage.Request

    @Test
    fun `string payload passes through unchanged`() {
        val message = request(""""{\"inappId\":\"inapp-1\"}"""")

        assertEquals("""{"inappId":"inapp-1"}""", message.payload)
    }

    @Test
    fun `object payload is kept as its json text`() {
        val message = request("""{"inappId":"inapp-1","params":{"a":1}}""")

        assertEquals(
            JsonParser.parseString("""{"inappId":"inapp-1","params":{"a":1}}"""),
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

    @Test
    fun `an action this SDK does not know survives parsing as unknown`() {
        // Gson writes null into the non-null action field for a name it has no constant for, and
        // the message then dies in the validator with an NPE — before reaching the dispatcher whose
        // whole job is to make sure the page hears something back.
        val message = gson.fromBridgeMessage(
            """{"type":"request","action":"teleport","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        )

        assertEquals(WebViewAction.UNKNOWN, (message as BridgeMessage.Request).action)
    }

    @Test
    fun `an unknown action is refused, not falsely acknowledged`() {
        var responded: String? = null
        var refused: Throwable? = null
        val message = gson.fromBridgeMessage(
            """{"type":"request","action":"teleport","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        ) as BridgeMessage.Request

        WebViewActionHandlers().dispatch(
            message = message,
            isUserPresent = true,
            launchSuspending = {},
            respond = { payload -> responded = payload },
            refuse = { error -> refused = error },
        )

        assertNull(responded)
        assertTrue(refused is IllegalArgumentException)
    }

    @Test
    fun `a missing action is answered too`() {
        val message = gson.fromBridgeMessage(
            """{"type":"request","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        )

        assertEquals(WebViewAction.UNKNOWN, (message as BridgeMessage.Request).action)
    }
}
