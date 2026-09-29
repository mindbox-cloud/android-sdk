package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import com.google.gson.JsonParser
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BridgeMessagePayloadTest {

    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private fun request(payloadJson: String): BridgeMessage.Request =
        gson.fromBridgeMessage(
            """{"type":"request","action":"showInApp","payload":$payloadJson,"id":"id-1","version":1,"timestamp":1}"""
        )?.message as BridgeMessage.Request

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
    fun `a coded refusal is answered with its code, not its detail`() {
        assertEquals(
            """{"error":"not_visible"}""",
            gson.toBridgeErrorPayload(BridgeRefusalException(BridgeErrorCode.NOT_VISIBLE, NOBODY_LOOKING_ERROR))
        )
    }

    @Test
    fun `an exception without a code is answered with internal_error, not its text`() {
        assertEquals(
            """{"error":"internal_error"}""",
            gson.toBridgeErrorPayload(IllegalStateException("Nobody is looking at this page"))
        )
    }

    @Test
    fun `an exception without a message is answered with internal_error`() {
        assertEquals(
            """{"error":"internal_error"}""",
            gson.toBridgeErrorPayload(IllegalStateException())
        )
    }

    @Test
    fun `the error codes are the contract list in the contract order`() {
        assertEquals(
            listOf(
                "unknown_action",
                "not_served",
                "not_visible",
                "invalid_payload",
                "unsupported_value",
                "invalid_url",
                "blocked_scheme",
                "open_failed",
                "permission_failed",
                "gestures_unavailable",
                "operation_failed",
                "unknown_inapp",
                "show_failed",
                "internal_error",
            ),
            BridgeErrorCode.entries.map { code -> code.wireValue }
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
        )?.message

        assertEquals(WebViewAction.UNKNOWN, (message as BridgeMessage.Request).action)
    }

    @Test
    fun `an unknown action keeps the name the page sent`() {
        listOf("show", "", "OPEN_LINK").forEach { name ->
            val received = gson.fromBridgeMessage(
                """{"type":"request","action":"$name","payload":{},"id":"id-1","version":1,"timestamp":1}"""
            )

            assertEquals(name, WebViewAction.UNKNOWN, received?.message?.action)
            assertEquals(name, name, received?.sentAction)
        }
    }

    @Test
    fun `the answer to an unknown action carries the name the page sent`() {
        val received = gson.fromBridgeMessage(
            """{"type":"request","action":"show","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        )!!
        val error = BridgeMessage.createErrorAction(received.message as BridgeMessage.Request, """{"error":"unknown_action"}""")

        val json = JsonParser.parseString(gson.toBridgeJson(error, received.sentAction)).asJsonObject

        assertEquals("show", json.get("action").asString)
        assertEquals("id-1", json.get("id").asString)
        assertEquals("error", json.get("type").asString)
    }

    @Test
    fun `the answer to a known action carries its wire name, whatever the page sent`() {
        val request = gson.fromBridgeMessage(
            """{"type":"request","action":"openLink","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        )!!.message as BridgeMessage.Request
        val error = BridgeMessage.createErrorAction(request, """{"error":"invalid_url"}""")

        val json = JsonParser.parseString(gson.toBridgeJson(error, sentAction = "somethingElse")).asJsonObject

        assertEquals("openLink", json.get("action").asString)
    }

    @Test
    fun `an unknown action is refused, not falsely acknowledged`() {
        var responded: String? = null
        var refused: Throwable? = null
        val message = gson.fromBridgeMessage(
            """{"type":"request","action":"teleport","payload":{},"id":"id-1","version":1,"timestamp":1}"""
        )?.message as BridgeMessage.Request

        WebViewActionHandlers().dispatch(
            message = message,
            isUserPresent = true,
            launchSuspending = {},
            respond = { payload -> responded = payload },
            refuse = { error -> refused = error },
        )

        assertNull(responded)
        assertEquals(BridgeErrorCode.UNKNOWN_ACTION, (refused as BridgeRefusalException).code)
    }

    @Test
    fun `a request whose action is missing or not a string is dropped`() {
        listOf("", """"action":null,""", """"action":5,""", """"action":true,""", """"action":{},""", """"action":["init"],""")
            .forEach { actionMember ->
                val received = gson.fromBridgeMessage(
                    """{"type":"request",$actionMember"payload":{},"id":"id-1","version":1,"timestamp":1}"""
                )

                assertNull(actionMember, received)
            }
    }

    @Test
    fun `an unknown action without an id is dropped without throwing`() {
        listOf("", """"id":null,""").forEach { idMember ->
            val received = gson.fromBridgeMessage(
                """{"type":"request","action":"show","payload":{},$idMember"version":1,"timestamp":1}"""
            )

            assertFalse(idMember, BridgeMessageValidator().isValid(received?.message))
        }
    }

    @Test
    fun `a response or an error without an action is still read so it can be matched by id`() {
        listOf("response", "error").forEach { type ->
            val received = gson.fromBridgeMessage(
                """{"type":"$type","payload":{"success":true},"id":"id-1","version":1,"timestamp":1}"""
            )

            assertEquals(type, "id-1", received?.message?.id)
            assertTrue(type, BridgeMessageValidator().isValid(received?.message))
        }
    }
}
