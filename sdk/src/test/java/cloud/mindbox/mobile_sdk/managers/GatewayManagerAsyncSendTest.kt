package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.MindboxError
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.mockLogger
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayManagerAsyncSendTest {

    private val serviceGenerator: MindboxServiceGenerator = mockk(relaxed = true)
    private val gatewayManager = GatewayManager(serviceGenerator)
    private var lastRequest: MindboxRequest? = null

    private val configuration = Configuration(
        configurationId = 1L,
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "test-endpoint-id",
        domain = "api.mindbox.ru",
        packageName = "com.test.app",
        versionName = "1.0.0",
        versionCode = "1",
        subscribeCustomerIfCreated = true,
        shouldCreateCustomer = true,
    )

    private val responseBodies = listOf(
        "",
        "<html><body>Too Many Requests</body></html>",
        """{"status":"ProtocolError","errorMessage":"Too many requests","httpStatusCode":429}""",
        """{"status":429}""",
        """{"status":"ValidationError","validationMessages":[]}""",
        """{"status":"Success"}""",
        """{"status":"TransactionAlreadyProcessed"}""",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockLogger()
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.deviceUuid } returns "device-uuid"
        every { MindboxPreferences.operationsDomainFromConfig } returns null
        every { serviceGenerator.addToRequestQueue(any<MindboxRequest>()) } answers {
            lastRequest = firstArg()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `429 keeps the event and throttles the operation whatever the body`() {
        responseBodies.forEach { body ->
            assertEquals("429 with body <$body>", AsyncEventSendResult.THROTTLED, sendAsyncAnswering(429, body))
        }
        verify(exactly = responseBodies.size) { MindboxLoggerImpl.i(any(), "[GD] decision: HTTP 429 → keep") }
    }

    @Test
    fun `5xx keeps the event without throttling the operation whatever the body`() {
        listOf(500, 502, 503, 504).forEach { code ->
            responseBodies.forEach { body ->
                assertEquals("$code with body <$body>", AsyncEventSendResult.RETRY_LATER, sendAsyncAnswering(code, body))
            }
        }
        verify(exactly = responseBodies.size) { MindboxLoggerImpl.i(any(), "[GD] decision: HTTP 503 → keep") }
    }

    @Test
    fun `other 4xx keep the decision made from the response body`() {
        val cases = listOf(
            Triple(400, """{"status":"ProtocolError","errorMessage":"bad","httpStatusCode":400}""", AsyncEventSendResult.DISCARDED),
            Triple(400, """{"status":"ValidationError","validationMessages":[]}""", AsyncEventSendResult.DISCARDED),
            Triple(403, """{"status":"Unknown"}""", AsyncEventSendResult.DISCARDED),
            Triple(400, """{"status":"Success"}""", AsyncEventSendResult.DELIVERED),
            Triple(404, "", AsyncEventSendResult.RETRY_LATER),
            Triple(404, "<html>Not Found</html>", AsyncEventSendResult.RETRY_LATER),
            Triple(400, "{}", AsyncEventSendResult.RETRY_LATER),
        )
        cases.forEach { (code, body, expected) ->
            assertEquals("$code with body <$body>", expected, sendAsyncAnswering(code, body))
        }
        verify { MindboxLoggerImpl.i(any(), "[GD] decision: HTTP 400 → delete") }
        verify { MindboxLoggerImpl.i(any(), "[GD] decision: HTTP 404 → keep") }
    }

    @Test
    fun `no response keeps the event without throttling the operation`() {
        val result = sendAsync { request -> request.deliverError(VolleyError("timeout")) }

        assertEquals(AsyncEventSendResult.RETRY_LATER, result)
        verify { MindboxLoggerImpl.i(any(), "[GD] decision: no response → keep") }
    }

    @Test
    fun `successful response delivers the event`() {
        val response: JSONObject = mockk { every { this@mockk.toString() } returns """{"status":"Success"}""" }

        val result = sendAsync { request -> request.listener!!.onResponse(response) }

        assertEquals(AsyncEventSendResult.DELIVERED, result)
    }

    @Test
    fun `event created before the clock was moved back is sent with zero time offset`() {
        val createdBeforeClockMovedBack = System.currentTimeMillis().toTimestamp() + 3_600_000L

        sendAsync(Event(eventType = EventType.AsyncOperation("Test.Operation"), enqueueTimestamp = createdBeforeClockMovedBack.ms)) {}

        assertEquals("0", lastRequest!!.timeOffsetQuery())
    }

    @Test
    fun `event created five minutes ago is sent with its age as time offset`() {
        val createdFiveMinutesAgo = System.currentTimeMillis().toTimestamp() + -300_000L

        sendAsync(Event(eventType = EventType.AsyncOperation("Test.Operation"), enqueueTimestamp = createdFiveMinutesAgo.ms)) {}

        assertTrue(lastRequest!!.timeOffsetQuery().toLong() in 300_000L..360_000L)
    }

    @Test
    fun `sync operation still receives 429 as a protocol error`() {
        val error = sendSyncAnsweringError(429, """{"status":"ProtocolError","errorMessage":"Too many requests","httpStatusCode":429}""")

        assertTrue("error was $error", error is MindboxError.Protocol)
        assertEquals(429, error?.statusCode)
    }

    @Test
    fun `sync operation still receives a success body of 5xx as success`() {
        val latch = CountDownLatch(1)
        var succeeded = false
        gatewayManager.sendEvent(
            configuration = configuration,
            deviceUuid = "device-uuid",
            event = Event(eventType = EventType.SyncOperation("Test.Sync")),
            shouldCountOffset = false,
            onSuccess = {
                succeeded = true
                latch.countDown()
            },
            onError = { latch.countDown() },
        )
        lastRequest!!.deliverError(volleyError(500, """{"status":"Success"}"""))

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(succeeded)
    }

    private fun sendAsyncAnswering(code: Int, body: String): AsyncEventSendResult =
        sendAsync { request -> request.deliverError(volleyError(code, body)) }

    private fun sendAsync(answer: (MindboxRequest) -> Unit): AsyncEventSendResult {
        val latch = CountDownLatch(1)
        var result: AsyncEventSendResult? = null
        sendAsync(Event(eventType = EventType.AsyncOperation("Test.Operation"))) {
            result = it
            latch.countDown()
        }
        answer(lastRequest!!)
        assertTrue("no result reported", latch.await(5, TimeUnit.SECONDS))
        return result!!
    }

    private fun sendAsync(event: Event, onResult: (AsyncEventSendResult) -> Unit) {
        gatewayManager.sendAsyncEvent(
            configuration = configuration,
            deviceUuid = "device-uuid",
            event = event,
            shouldCountOffset = true,
            onResult = onResult,
        )
    }

    private fun MindboxRequest.timeOffsetQuery(): String =
        checkNotNull(Regex("[?&]dateTimeOffset=([^&]*)").find(url)).groupValues[1]

    private fun sendSyncAnsweringError(code: Int, body: String): MindboxError? {
        val latch = CountDownLatch(1)
        var error: MindboxError? = null
        gatewayManager.sendEvent(
            configuration = configuration,
            deviceUuid = "device-uuid",
            event = Event(eventType = EventType.SyncOperation("Test.Sync")),
            shouldCountOffset = false,
            onSuccess = { latch.countDown() },
            onError = {
                error = it
                latch.countDown()
            },
        )
        lastRequest!!.deliverError(volleyError(code, body))
        assertTrue("no callback", latch.await(5, TimeUnit.SECONDS))
        return error
    }

    private fun volleyError(code: Int, body: String) =
        VolleyError(NetworkResponse(code, body.toByteArray(), false, 0L, emptyList()))
}
