package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.network.PushDeliveredRequest
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.VolleyError
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PushDeliveredManagerTest {

    private val now = 1_000_000_000L
    private val timeProvider = object : TimeProvider {
        override fun currentTimeMillis(): Long = now

        override fun currentTimestamp(): Timestamp = Timestamp(now)

        override fun elapsedSince(startTimeMillis: Timestamp): Milliseconds = Milliseconds(now - startTimeMillis.ms)

        override fun monotonicMillis(): Milliseconds = Milliseconds(0L)

        override fun monotonicElapsedSince(startTick: Milliseconds): Milliseconds = Milliseconds(0L)
    }
    private val serviceGenerator: MindboxServiceGenerator = mockk(relaxed = true)
    private val manager = PushDeliveredManager(serviceGenerator, timeProvider)
    private var lastRequest: PushDeliveredRequest? = null

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

    @Before
    fun setUp() {
        mockLogger()
        every { serviceGenerator.addToRequestQueue(any<PushDeliveredRequest>()) } answers { lastRequest = firstArg() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun delivered(url: String?, enqueueTimestamp: Long = now) = Event(
        eventType = EventType.PushDelivered,
        enqueueTimestamp = enqueueTimestamp,
        additionalFields = url?.let { hashMapOf(EventType.PushDelivered.FIELD_URL to it) },
    )

    private fun send(event: Event, shouldCountOffset: Boolean = false): List<AsyncEventSendResult> {
        val results = mutableListOf<AsyncEventSendResult>()
        manager.send(
            configuration = configuration,
            event = event,
            shouldCountOffset = shouldCountOffset,
            onResult = { results.add(it) },
        )
        return results
    }

    private fun volleyError(code: Int, body: String = "") =
        VolleyError(NetworkResponse(code, body.toByteArray(), false, 0L, emptyList()))

    @Test
    fun `confirmation is a POST to the url from the push with only dateTimeOffset added`() {
        send(delivered("https://mbx.ru/d/key/hash?sig=1"))

        val request = checkNotNull(lastRequest)
        assertEquals(Request.Method.POST, request.method)
        assertEquals("https://mbx.ru/d/key/hash?sig=1&dateTimeOffset=0", request.url)
    }

    @Test
    fun `retry counts dateTimeOffset from the moment the push was received`() {
        send(delivered("https://mbx.ru/d", enqueueTimestamp = now - 300_000L), shouldCountOffset = true)

        assertEquals("https://mbx.ru/d?dateTimeOffset=300000", checkNotNull(lastRequest).url)
    }

    @Test
    fun `receipt time ahead of the clock gives a zero offset`() {
        send(delivered("https://mbx.ru/d", enqueueTimestamp = now + 5_000L), shouldCountOffset = true)

        assertEquals("https://mbx.ru/d?dateTimeOffset=0", checkNotNull(lastRequest).url)
    }

    @Test
    fun `confirmation without a url is dropped without a request`() {
        assertEquals(listOf(AsyncEventSendResult.DISCARDED), send(delivered(null)))
        verify(exactly = 0) { serviceGenerator.addToRequestQueue(any<PushDeliveredRequest>()) }
    }

    @Test
    fun `stored url that is not http or https is dropped without a request`() {
        assertEquals(listOf(AsyncEventSendResult.DISCARDED), send(delivered("ftp://mbx.ru/d")))
        verify(exactly = 0) { serviceGenerator.addToRequestQueue(any<PushDeliveredRequest>()) }
    }

    @Test
    fun `answers follow the asynchronous operations rules`() {
        val cases = listOf(
            500 to AsyncEventSendResult.RETRY_LATER,
            503 to AsyncEventSendResult.RETRY_LATER,
            429 to AsyncEventSendResult.THROTTLED,
        )
        cases.forEach { (code, expected) ->
            val results = send(delivered("https://mbx.ru/d"))
            checkNotNull(lastRequest).deliverError(volleyError(code))
            assertEquals("HTTP $code", listOf(expected), results)
        }
    }

    @Test
    fun `4xx closes the confirmation whatever the body`() {
        listOf(
            400 to "",
            403 to "<html>Forbidden</html>",
            404 to "{}",
            410 to """{"status":"Success"}""",
        ).forEach { (code, body) ->
            val results = send(delivered("https://mbx.ru/d"))
            checkNotNull(lastRequest).deliverError(volleyError(code, body))
            assertEquals("HTTP $code with body <$body>", listOf(AsyncEventSendResult.DISCARDED), results)
        }
    }

    @Test
    fun `2xx answer delivers the confirmation`() {
        val results = send(delivered("https://mbx.ru/d"))
        checkNotNull(lastRequest).deliverResponse(Unit)

        assertEquals(listOf(AsyncEventSendResult.DELIVERED), results)
    }

    @Test
    fun `no answer keeps the confirmation`() {
        val results = send(delivered("https://mbx.ru/d"))
        checkNotNull(lastRequest).deliverError(VolleyError("timeout"))

        assertEquals(listOf(AsyncEventSendResult.RETRY_LATER), results)
    }
}
