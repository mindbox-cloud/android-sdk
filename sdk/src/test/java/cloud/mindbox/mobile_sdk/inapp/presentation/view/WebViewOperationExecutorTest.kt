package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.models.MindboxError
import cloud.mindbox.mobile_sdk.models.ValidationMessage
import com.google.gson.Gson
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebViewOperationExecutorTest {

    private lateinit var sender: FakeOperationSender
    private lateinit var mobileConfigRepository: MobileConfigRepository
    private lateinit var featureToggleManager: FeatureToggleManager
    private lateinit var executor: MindboxWebViewOperationExecutor

    private data class AsyncSent(val context: Application, val name: String, val body: String)

    private class FakeOperationSender : WebViewOperationSender {

        val sentAsync = mutableListOf<AsyncSent>()
        val sentSync = mutableListOf<Pair<String, String>>()
        var syncResponse: String? = null
        var syncError: MindboxError? = null

        override fun asyncOperation(context: Application, name: String, body: String) {
            sentAsync.add(AsyncSent(context, name, body))
        }

        override fun syncOperation(
            name: String,
            body: String,
            onSuccess: (String) -> Unit,
            onError: (MindboxError) -> Unit,
        ) {
            sentSync.add(name to body)
            syncError?.let { error ->
                onError(error)
                return
            }
            syncResponse?.let(onSuccess)
        }
    }

    @Before
    fun onTestStart() {
        sender = FakeOperationSender()
        mobileConfigRepository = mockk {
            every { findInAppInCurrentConfig(any()) } returns null
        }
        featureToggleManager = mockk {
            every { isEnabled(SEND_INAPP_TAGS_FEATURE) } returns true
        }
        executor = MindboxWebViewOperationExecutor(Gson(), mobileConfigRepository, featureToggleManager, sender)
    }

    @Test
    fun `executeAsyncOperation sends parsed operation and body to the sender`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = null)
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home"}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation adds top-level tags to body when tags present`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":{"templateType":"Popup"}}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation does not add tags when tags empty`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = emptyMap())
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home"}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation adds in-app tags when existing tags is json null`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":null}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":{"templateType":"Popup"}}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation merges in-app tags into existing tags without collision`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":{"client":"own"}}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":{"client":"own","templateType":"Popup"}}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation keeps client value and skips in-app value on key collision`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn"}}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":{"templateType":"ClientOwn"}}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation merges only non-colliding keys when tags partially overlap`() {
        val context: Application = mockk()
        val payload: String =
            """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn","keep":"x"}}}"""
        executor.executeAsyncOperation(
            context,
            payload,
            hostInAppId = "host-id",
            hostTags = mapOf("templateType" to "Popup", "campaign" to "summer"),
        )
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":{"templateType":"ClientOwn","keep":"x","campaign":"summer"}}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation keeps client tags untouched when existing tags is not an object`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":"raw"}}"""
        executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(
            listOf(AsyncSent(context, "OpenScreen", """{"screen":"home","tags":"raw"}""")),
            sender.sentAsync,
        )
    }

    @Test
    fun `executeAsyncOperation throws when payload misses operation`() {
        val context: Application = mockk()
        val payload: String = """{"body":{"screen":"home"}}"""
        try {
            executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = null)
            fail("Expected BridgeRefusalException")
        } catch (exception: BridgeRefusalException) {
            assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            assertEquals("Operation is not provided", exception.message)
        }
        assertEquals(emptyList<AsyncSent>(), sender.sentAsync)
    }

    @Test
    fun `executeAsyncOperation throws when payload misses body`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen"}"""
        try {
            executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = null)
            fail("Expected BridgeRefusalException")
        } catch (exception: BridgeRefusalException) {
            assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            assertEquals("Body is not provided", exception.message)
        }
        assertEquals(emptyList<AsyncSent>(), sender.sentAsync)
    }

    @Test
    fun `executeAsyncOperation refuses a null payload as invalid_payload`() {
        val context: Application = mockk()
        try {
            executor.executeAsyncOperation(context, payload = null, hostInAppId = "host-id", hostTags = null)
            fail("Expected BridgeRefusalException")
        } catch (exception: BridgeRefusalException) {
            assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            assertEquals("Payload is not provided", exception.message)
        }
        assertEquals(emptyList<AsyncSent>(), sender.sentAsync)
    }

    @Test
    fun `executeAsyncOperation refuses a payload that is not json as invalid_payload`() {
        val context: Application = mockk()
        val payloads: List<String> = listOf("not-json", "")
        payloads.forEach { payload: String ->
            try {
                executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = null)
                fail("Expected BridgeRefusalException for payload: $payload")
            } catch (exception: BridgeRefusalException) {
                assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
                assertEquals("Payload is not a valid JSON object", exception.message)
            }
        }
        assertEquals(emptyList<AsyncSent>(), sender.sentAsync)
    }

    @Test
    fun `executeAsyncOperation refuses an operation or a body of the wrong json type as invalid_payload`() {
        val context: Application = mockk()
        val payloads: List<String> = listOf(
            """{"operation":{},"body":{}}""",
            """{"operation":["X"],"body":{}}""",
            """{"operation":null,"body":{}}""",
            """{"operation":"OpenScreen","body":"str"}""",
            """{"operation":"OpenScreen","body":[1]}""",
            """{"operation":"OpenScreen","body":null}""",
        )
        payloads.forEach { payload: String ->
            try {
                executor.executeAsyncOperation(context, payload, hostInAppId = "host-id", hostTags = null)
                fail("Expected BridgeRefusalException for payload: $payload")
            } catch (exception: BridgeRefusalException) {
                assertEquals("payload $payload", BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            }
        }
        assertEquals(emptyList<AsyncSent>(), sender.sentAsync)
    }

    @Test
    fun `executeSyncOperation returns response when the sender succeeds`() = runTest {
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        val expectedResponse: String = """{"result":"ok"}"""
        sender.syncResponse = expectedResponse
        val actualResponse: String = executor.executeSyncOperation(payload, hostInAppId = "host-id", hostTags = null)
        assertEquals(expectedResponse, actualResponse)
        assertEquals(
            listOf("OpenScreen" to """{"screen":"home"}"""),
            sender.sentSync,
        )
    }

    @Test
    fun `executeSyncOperation adds top-level tags and still returns response`() = runTest {
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        val expectedResponse: String = """{"result":"ok"}"""
        sender.syncResponse = expectedResponse
        val actualResponse: String = executor.executeSyncOperation(payload, hostInAppId = "host-id", hostTags = mapOf("templateType" to "Popup"))
        assertEquals(expectedResponse, actualResponse)
        assertEquals(
            listOf("OpenScreen" to """{"screen":"home","tags":{"templateType":"Popup"}}"""),
            sender.sentSync,
        )
    }

    @Test
    fun `executeSyncOperation merges tags keeping client value on collision and still returns response`() = runTest {
        val payload: String =
            """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn"}}}"""
        val expectedResponse: String = """{"result":"ok"}"""
        sender.syncResponse = expectedResponse
        val actualResponse: String = executor.executeSyncOperation(
            payload,
            hostInAppId = "host-id",
            hostTags = mapOf("templateType" to "Popup", "campaign" to "summer"),
        )
        assertEquals(expectedResponse, actualResponse)
        assertEquals(
            listOf("OpenScreen" to """{"screen":"home","tags":{"templateType":"ClientOwn","campaign":"summer"}}"""),
            sender.sentSync,
        )
    }

    private fun executeSyncOperationExpectingError(error: MindboxError): WebViewSyncOperationException = runBlocking {
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        sender.syncError = error
        try {
            executor.executeSyncOperation(payload, hostInAppId = "host-id", hostTags = null)
            throw AssertionError("Expected WebViewSyncOperationException")
        } catch (exception: WebViewSyncOperationException) {
            exception
        }
    }

    @Test
    fun `executeSyncOperation protocol error payload is the data contents in iOS format`() {
        val exception = executeSyncOperationExpectingError(
            MindboxError.Protocol(
                statusCode = 400,
                status = "ProtocolError",
                errorMessage = "Operation OpenScreen not found",
                errorId = "error-id-1",
                httpStatusCode = 400,
            )
        )
        assertEquals(
            """{"status":"ProtocolError","errorMessage":"Operation OpenScreen not found","errorId":"error-id-1","httpStatusCode":"400"}""",
            exception.payloadJson,
        )
    }

    @Test
    fun `executeSyncOperation internal server error payload is the data contents in iOS format`() {
        val exception = executeSyncOperationExpectingError(
            MindboxError.InternalServer(
                statusCode = 500,
                status = "InternalServerError",
                errorMessage = "Something went wrong",
                errorId = null,
                httpStatusCode = 500,
            )
        )
        assertEquals(
            """{"status":"InternalServerError","errorMessage":"Something went wrong","errorId":"","httpStatusCode":"500"}""",
            exception.payloadJson,
        )
    }

    @Test
    fun `executeSyncOperation validation error payload is the data contents with validationMessages`() {
        val exception = executeSyncOperationExpectingError(
            MindboxError.Validation(
                statusCode = 200,
                status = "ValidationError",
                validationMessages = listOf(
                    ValidationMessage(message = "Invalid email", location = "/customer/email")
                ),
            )
        )
        assertEquals(
            """{"status":"ValidationError","validationMessages":[{"message":"Invalid email","location":"/customer/email"}]}""",
            exception.payloadJson,
        )
    }

    @Test
    fun `executeSyncOperation network error payload is the data contents without envelope`() {
        val exception = executeSyncOperationExpectingError(MindboxError.UnknownServer())
        assertEquals(
            """{"errorMessage":"Cannot reach server","httpStatusCode":"null"}""",
            exception.payloadJson,
        )
    }

    @Test
    fun `executeSyncOperation unknown error payload is the data contents without envelope`() {
        val exception = executeSyncOperationExpectingError(MindboxError.Unknown(Throwable("network failure")))
        assertEquals(
            """{"errorKey":"unknown","errorName":"java.lang.Throwable","errorMessage":"network failure"}""",
            exception.payloadJson,
        )
    }

    @Test
    fun `executeSyncOperation throws when payload misses body`() = runTest {
        val payload: String = """{"operation":"OpenScreen"}"""
        try {
            executor.executeSyncOperation(payload, hostInAppId = "host-id", hostTags = null)
            fail("Expected BridgeRefusalException")
        } catch (exception: BridgeRefusalException) {
            assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            assertEquals("Body is not provided", exception.message)
        }
        assertEquals(emptyList<Pair<String, String>>(), sender.sentSync)
    }

    @Test
    fun `executeSyncOperation refuses a null payload as invalid_payload`() = runTest {
        try {
            executor.executeSyncOperation(payload = null, hostInAppId = "host-id", hostTags = null)
            fail("Expected BridgeRefusalException")
        } catch (exception: BridgeRefusalException) {
            assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
            assertEquals("Payload is not provided", exception.message)
        }
        assertEquals(emptyList<Pair<String, String>>(), sender.sentSync)
    }

    @Test
    fun `executeSyncOperation refuses a payload that is not json as invalid_payload`() = runTest {
        val payloads: List<String> = listOf("not-json", "")
        payloads.forEach { payload: String ->
            try {
                executor.executeSyncOperation(payload, hostInAppId = "host-id", hostTags = null)
                fail("Expected BridgeRefusalException for payload: $payload")
            } catch (exception: BridgeRefusalException) {
                assertEquals(BridgeErrorCode.INVALID_PAYLOAD, exception.code)
                assertEquals("Payload is not a valid JSON object", exception.message)
            }
        }
        assertEquals(emptyList<Pair<String, String>>(), sender.sentSync)
    }
}
