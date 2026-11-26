package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.network.PushDeliveredRequest
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
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

    private fun delivery(url: String = "https://mbx.ru/d", receivedAt: Long = now) =
        PushDelivery(id = 7L, url = url, receivedAt = receivedAt)

    private fun TestScope.send(delivery: PushDelivery = delivery()): Deferred<AsyncEventSendResult> {
        val result = async { manager.send(configuration, delivery) }
        runCurrent()
        return result
    }

    private fun volleyError(code: Int, body: String = "") =
        VolleyError(NetworkResponse(code, body.toByteArray(), false, 0L, emptyList()))

    @Test
    fun `confirmation is a POST to the url from the push with only dateTimeOffset added`() = runTest {
        val result = send(delivery("https://mbx.ru/d/key/hash?sig=1"))

        val request = checkNotNull(lastRequest)
        assertEquals(Request.Method.POST, request.method)
        assertEquals("https://mbx.ru/d/key/hash?sig=1&dateTimeOffset=0", request.url)
        result.cancel()
    }

    @Test
    fun `every attempt counts dateTimeOffset from the moment the push was received`() = runTest {
        val first = send(delivery(receivedAt = now - 60L))
        assertEquals("https://mbx.ru/d?dateTimeOffset=60", checkNotNull(lastRequest).url)
        first.cancel()

        val later = send(delivery(receivedAt = now - 300_000L))
        assertEquals("https://mbx.ru/d?dateTimeOffset=300000", checkNotNull(lastRequest).url)
        later.cancel()
    }

    @Test
    fun `receipt time ahead of the clock gives a zero offset`() = runTest {
        val result = send(delivery(receivedAt = now + 5_000L))

        assertEquals("https://mbx.ru/d?dateTimeOffset=0", checkNotNull(lastRequest).url)
        result.cancel()
    }

    @Test
    fun `stored url that is not https is dropped without a request`() = runTest {
        listOf("ftp://mbx.ru/d", "http://mbx.ru/d", "").forEach { url ->
            assertEquals(url, AsyncEventSendResult.DISCARDED, send(delivery(url)).await())
        }
        verify(exactly = 0) { serviceGenerator.addToRequestQueue(any<PushDeliveredRequest>()) }
    }

    @Test
    fun `5xx keeps the confirmation and 429 marks it throttled`() = runTest {
        listOf(
            500 to AsyncEventSendResult.RETRY_LATER,
            503 to AsyncEventSendResult.RETRY_LATER,
            429 to AsyncEventSendResult.THROTTLED,
        ).forEach { (code, expected) ->
            val result = send()
            checkNotNull(lastRequest).deliverError(volleyError(code))
            assertEquals("HTTP $code", expected, result.await())
        }
    }

    @Test
    fun `3xx and 4xx close the confirmation whatever the body`() = runTest {
        listOf(
            302 to "",
            400 to "",
            403 to "<html>Forbidden</html>",
            404 to "{}",
            410 to """{"status":"Success"}""",
        ).forEach { (code, body) ->
            val result = send()
            checkNotNull(lastRequest).deliverError(volleyError(code, body))
            assertEquals("HTTP $code with body <$body>", AsyncEventSendResult.DISCARDED, result.await())
        }
    }

    @Test
    fun `2xx answer delivers the confirmation`() = runTest {
        val result = send()
        checkNotNull(lastRequest).deliverResponse(Unit)

        assertEquals(AsyncEventSendResult.DELIVERED, result.await())
    }

    @Test
    fun `socket timeout keeps the confirmation`() = runTest {
        val result = send()
        checkNotNull(lastRequest).deliverError(TimeoutError())

        assertEquals(AsyncEventSendResult.RETRY_LATER, result.await())
    }

    @Test
    fun `refused connection keeps the confirmation`() = runTest {
        val result = send()
        checkNotNull(lastRequest).deliverError(NoConnectionError(ConnectException("refused")))

        assertEquals(AsyncEventSendResult.RETRY_LATER, result.await())
    }

    @Test
    fun `attempt without an answer ends after 2500 ms and cancels its request`() = runTest {
        val result = send()
        val request = checkNotNull(lastRequest)

        advanceTimeBy(2_499L)
        runCurrent()
        assertFalse(result.isCompleted)
        assertFalse(request.isCanceled)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(AsyncEventSendResult.RETRY_LATER, result.await())
        assertTrue(request.isCanceled)
    }

    @Test
    fun `request waits on the socket no longer than the attempt lasts`() = runTest {
        send()

        assertEquals(2_500, checkNotNull(lastRequest).timeoutMs)
    }

    @Test
    fun `answer after the deadline does not change the result`() = runTest {
        val result = send()
        val request = checkNotNull(lastRequest)
        advanceTimeBy(2_500L)
        runCurrent()

        request.deliverResponse(Unit)

        assertEquals(AsyncEventSendResult.RETRY_LATER, result.await())
    }

    @Test
    fun `cancelled attempt cancels its request`() = runTest {
        val result = send()
        val request = checkNotNull(lastRequest)

        result.cancel()
        runCurrent()

        assertTrue(request.isCanceled)
        assertTrue(result.isCancelled)
    }
}
