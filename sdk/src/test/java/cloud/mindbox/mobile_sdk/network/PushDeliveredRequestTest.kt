package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.mockLogger
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.VolleyError
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushDeliveredRequestTest {

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
    private var deliveredCount = 0

    private val request = PushDeliveredRequest(
        url = "https://mbx.ru/d/key/hash?sig=secret&dateTimeOffset=0",
        configuration = configuration,
        timeout = Milliseconds(2_500L),
        onDelivered = { deliveredCount++ },
        errorListener = {},
    )

    @Before
    fun setUp() {
        mockLogger()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun ok(body: String) = NetworkResponse(200, body.toByteArray(), false, 0L, emptyList())

    @Test
    fun `any 2xx body counts as delivered`() {
        listOf("", "OK", "Delivered OK", "<html><body>Accepted</body></html>", "{").forEach { body ->
            assertTrue(body, request.parseNetworkResponse(ok(body)).isSuccess)
        }
    }

    @Test
    fun `successful answer reports delivery`() {
        request.deliverResponse(Unit)

        assertEquals(1, deliveredCount)
    }

    @Test
    fun `confirmation is a POST with an empty JSON object and the operations headers`() {
        assertEquals(Request.Method.POST, request.method)
        assertEquals("{}", request.body.toString(Charsets.UTF_8))
        assertEquals(request.headers["Content-Type"], request.bodyContentType)
        assertEquals(MindboxRequest(configuration = configuration).headers, request.headers)
    }

    @Test
    fun `confirmation is not cached and waits the given time without retries`() {
        assertFalse(request.shouldCache())
        assertEquals(2_500, request.timeoutMs)
        assertThrows(VolleyError::class.java) { request.retryPolicy.retry(VolleyError("timeout")) }
    }

    @Test
    fun `confirmation goes ahead of the operations waiting in the request queue`() {
        assertEquals(Request.Priority.IMMEDIATE, request.priority)
    }
}
